package es.codeurjc.squirrel.drey;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.config.Config;
import com.hazelcast.config.EntryListenerConfig;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.spi.exception.DistributedObjectDestroyedException;

import es.codeurjc.squirrel.drey.Algorithm.Status;

/**
 * @author Pablo Fuente (pablo.fuente@urjc.es)
 */
public class AlgorithmManager<R> {

	private static final Logger log = LoggerFactory.getLogger(AlgorithmManager.class);

	HazelcastInstance hzClient;
	QueuesManager queuesManager;

	Map<String, Algorithm<R>> algorithms;
	Map<String, AtomicLong> taskCompletedEventsCount;
	Map<String, ReentrantLock> taskCompletedLocks;
	Map<String, ReentrantLock> taskTimeoutLocks;
	Map<String, Map<String, String>> algorithmStructures;
	Map<String, Boolean> algorithmsMarkedWithTimeout;
	Map<String, Integer> algorithmsRunningAndFinishedTasksOnTimeout;
	Map<String, WorkerStats> workers;
	IMap<String, QueueProperty> QUEUES;

	CountDownLatch terminateBlockingLatch;
	Map<String, CountDownLatch> terminateOneBlockingLatches;
	long timeForTerminate;

	boolean withAWSCloudWatch = false;
	CloudWatchModule cloudWatchModule;

	public AlgorithmManager(String HAZELCAST_CONFIG, boolean withAWSCloudWatch) {

		boolean developmentMode = System.getProperty("devmode") != null ? Boolean.valueOf(System.getProperty("devmode"))
				: false;

		if (developmentMode) {
			new Thread(() -> Worker.launch()).start();
			new Thread(() -> Worker.launch()).start();
		}

		Config config = new Config();
		try {
			config = new FileSystemXmlConfig(HAZELCAST_CONFIG);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		config.getCPSubsystemConfig().setCPMemberCount(3);

		Mode mode = System.getProperty("mode") != null ? Mode.valueOf(System.getProperty("mode")) : Mode.RANDOM;
		this.queuesManager = new QueuesManager(mode);

		MapOfQueuesListener mapOfQueuesListener = new MapOfQueuesListener(queuesManager);

		MapConfig mapConfig = new MapConfig();
		mapConfig.setName("QUEUES");
		mapConfig.addEntryListenerConfig(new EntryListenerConfig(mapOfQueuesListener, false, true));
		config.addMapConfig(mapConfig);

		this.hzClient = Hazelcast.newHazelcastInstance(config);

		int idleCores = System.getProperty("idle-cores-app") != null
				? Integer.parseInt(System.getProperty("idle-cores-app"))
				: new Double(Math.ceil(Runtime.getRuntime().availableProcessors() * 0.75)).intValue();

		log.info("Application worker will have {} idle cores", idleCores);

		this.queuesManager.initializeHazelcast(hzClient, idleCores);
		this.hzClient.getCluster().addMembershipListener(new ClusterMembershipListener(this, hzClient));

		this.algorithms = new ConcurrentHashMap<>();
		this.taskCompletedEventsCount = new ConcurrentHashMap<>();
		this.taskCompletedLocks = new ConcurrentHashMap<>();
		this.taskTimeoutLocks = new ConcurrentHashMap<>();
		this.algorithmStructures = new ConcurrentHashMap<>();
		this.algorithmsMarkedWithTimeout = new ConcurrentHashMap<>();
		this.algorithmsRunningAndFinishedTasksOnTimeout = new ConcurrentHashMap<>();
		this.workers = new ConcurrentHashMap<>();

		this.QUEUES = this.hzClient.getMap("QUEUES");

		this.terminateOneBlockingLatches = new ConcurrentHashMap<>();

		this.withAWSCloudWatch = withAWSCloudWatch;
		if (this.withAWSCloudWatch)
			this.cloudWatchModule = new CloudWatchModule(this.hzClient, this.QUEUES);

		hzClient.getTopic("task-completed").addMessageListener(message -> {
			AlgorithmEvent ev = (AlgorithmEvent) message.getMessageObject();
			Task t = (Task) ev.getContent();
			log.info("TASK [{}] completed for algorithm [{}]. Took {} ms", t, ev.getAlgorithmId(),
					System.currentTimeMillis() - t.getTimeStarted());
			Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());

			try {
				hzClient.getCPSubsystem().getAtomicLong("completed" + ev.getAlgorithmId()).incrementAndGet();
			} catch (DistributedObjectDestroyedException e) {
				log.warn("Task {} completed for algorithm {} but it has been already terminated. Message: {} ", t,
						ev.getAlgorithmId(), e.getMessage());
				return;
			}

			ReentrantLock l = this.taskCompletedLocks.get(ev.getAlgorithmId());
			l.lock();
			try {
				if (alg == null) {
					// Interruption of algorithm. This task is being terminated after stopped
					log.info("TASK {} COMPLETED FOR STOPPED ALGORITHM {}: ", t, ev.getAlgorithmId());
					this.cleanAlgorithmStructures(ev.getAlgorithmId());
				} else {

					final String algId = alg.getId();

					algorithmStructures.get(algId).putAll(t.getHazelcastStructures());
					if (t.getFinalResult() != null)
						alg.setResult((R) t.getFinalResult());

					if (alg.hasSuccessullyFinished(this.taskCompletedEventsCount.get(algId).incrementAndGet())) {
						log.info("ALGORITHM SOLVED: Algorithm: {}, Result: {}, Last task: {}", algId,
								t.getFinalResult(), t);
						try {
							alg.runCallbackSuccess();
						} catch (Exception e) {
							log.error(e.getMessage());
						}

						cleanAlgorithmStructures(algId);
					} else if (this.algorithmsMarkedWithTimeout.get(algId)) {
						log.warn("ALGORITHM TIMEOUT: Algorithm: {}, Completed task: {}", algId, t);
						final Integer runningAndFinishedTasks = this.algorithmsRunningAndFinishedTasksOnTimeout
								.get(algId);
						if (alg.hasFinishedRunningTasks(runningAndFinishedTasks)) {
							try {
								log.warn("Last running task [{}] in algorithm [{}]. Starting algorithm termination", t,
										algId);
								this.blockingTerminateOneAlgorithm(algId);
							} catch (InterruptedException e) {
								log.error(
										"Error while forcibly terminating algorithm [{}] for task [{}] triggering timeout: {}",
										algId, t, e.getMessage());
							}
							alg.runCallbackError(Status.TIMEOUT);
						} else {
							log.warn(
									"There are still running tasks in algorithm [{}]. Last running task will trigger algorithm termination by timeout",
									algId);
						}
					}
				}
			} finally {
				l.unlock();
			}
		});
		hzClient.getTopic("task-timeout").addMessageListener((message) -> {
			AlgorithmEvent ev = (AlgorithmEvent) message.getMessageObject();
			Task t = (Task) ev.getContent();
			log.warn("TASK [{}] timeout ({} ms) for algorithm [{}]", t, t.getMaxDuration(), ev.getAlgorithmId());
			Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());

			try {
				hzClient.getCPSubsystem().getAtomicLong("timeout" + ev.getAlgorithmId()).incrementAndGet();
			} catch (DistributedObjectDestroyedException e) {
				log.warn("Task {} timeout for algorithm {} but it has been already terminated. Message: {} ", t,
						ev.getAlgorithmId(), e.getMessage());
				return;
			}

			ReentrantLock l = this.taskTimeoutLocks.get(ev.getAlgorithmId());
			l.lock();
			try {

				if (alg == null) {
					// Interruption of algorithm. This task is triggering timeout after stopped
					log.info("TASK {} TRIGGERED TIMEOUT FOR STOPPED ALGORITHM {}: ", t, ev.getAlgorithmId());
					this.cleanAlgorithmStructures(ev.getAlgorithmId());
				} else {

					final String algId = alg.getId();

					int runningAndFinishedTasks;
					final Integer previouslyStoredTasks = this.algorithmsRunningAndFinishedTasksOnTimeout.get(algId);
					if (previouslyStoredTasks == null) {
						// First timeout task of the algorithm
						runningAndFinishedTasks = alg.getTasksAdded() - alg.getTasksQueued();
						log.info("Algorithm [{}] has {} running tasks when termination timeout caused by task [{}]",
								algId, runningAndFinishedTasks - alg.getTasksCompleted(), t);

						this.algorithmsRunningAndFinishedTasksOnTimeout.put(algId, runningAndFinishedTasks);
						IQueue<Task> queue = this.hzClient.getQueue(algId);
						queue.clear();
						queue.destroy();

						log.info(
								"Task queue for algorithm [{}] has been emptied because of timeout termination caused by task [{}]",
								algId, t);
					} else {
						// Other timeout tasks
						runningAndFinishedTasks = previouslyStoredTasks;
					}

					if (alg.hasFinishedRunningTasks(runningAndFinishedTasks)) {
						try {
							log.warn("Last running task [{}] in algorithm [{}]. Starting algorithm termination", t,
									algId);
							this.blockingTerminateOneAlgorithm(algId);
						} catch (InterruptedException e) {
							log.error(
									"Error while forcibly terminating algorithm [{}] for task [{}] triggering timeout: {}",
									algId, t, e.getMessage());
						}
						alg.runCallbackError(Status.TIMEOUT);
					} else {
						log.warn(
								"There are still running tasks in algorithm [{}]. Last running task will trigger algorithm termination by timeout",
								algId);
						this.algorithmsMarkedWithTimeout.put(algId, true);
					}
				}
			} finally {
				l.unlock();
			}
		});
		hzClient.getTopic("stop-algorithms-done").addMessageListener((message) -> {
			log.info("Algorithms successfully terminated on {} milliseconds",
					System.currentTimeMillis() - this.timeForTerminate);
			this.terminateBlockingLatch.countDown();
		});
		hzClient.getTopic("stop-one-algorithm-done").addMessageListener((message) -> {
			log.info("Algorithm [{}] successfully terminated", message.getMessageObject());
			this.terminateOneBlockingLatches.get((String) message.getMessageObject()).countDown();
		});
		hzClient.getTopic("worker-stats").addMessageListener((message) -> {
			WorkerEvent ev = (WorkerEvent) message.getMessageObject();
			log.debug("WORKER EVENT for worker [{}]: {}", ev.getWorkerId(), ev.getContent());
			this.workers.put(ev.getWorkerId(), (WorkerStats) ev.getContent());
		});
	}

	private Algorithm<R> cleanAlgorithmStructures(String algorithmId) {

		this.hzClient.getQueue(algorithmId).destroy();

		if (this.algorithmStructures.get(algorithmId) != null) {
			for (String structureId : this.algorithmStructures.get(algorithmId).keySet()) {
				this.getHazelcastStructure(structureId).destroy();
			}
			log.info("Destroyed {} Hazelcast Data Structures for algorithm {}: {}",
					this.algorithmStructures.get(algorithmId).keySet().size(), algorithmId,
					this.algorithmStructures.get(algorithmId).keySet());
		}

		this.hzClient.getCPSubsystem().getAtomicLong("added" + algorithmId).destroy();
		this.hzClient.getCPSubsystem().getAtomicLong("completed" + algorithmId).destroy();
		this.hzClient.getCPSubsystem().getAtomicLong("timeout" + algorithmId).destroy();

		// Remove algorithm
		Algorithm<R> alg = this.algorithms.remove(algorithmId);
		// Remove the count of task completed events
		this.taskCompletedEventsCount.remove(algorithmId);
		// Remove the completed locks for this algorithm
		this.taskCompletedLocks.remove(algorithmId);
		// Remove the tiemout locks for this algorithm
		this.taskTimeoutLocks.remove(algorithmId);
		// Remove algorithm distributed structures
		this.algorithmStructures.remove(algorithmId);
		// Remove algorithm timeout marks
		this.algorithmsMarkedWithTimeout.remove(algorithmId);
		// Remove algorithm running tasks on timeout
		this.algorithmsRunningAndFinishedTasksOnTimeout.remove(algorithmId);
		// Remove distributed task queue
		this.QUEUES.remove(algorithmId);

		return alg;
	}

	private DistributedObject getHazelcastStructure(String id) {
		HazelcastStructure struct = HazelcastStructure.valueOf(id.substring(id.lastIndexOf("-") + 1));
		DistributedObject object = null;
		switch (struct) {
		case MAP:
			object = this.hzClient.getMap(id);
			break;
		case QUEUE:
			object = this.hzClient.getQueue(id);
			break;
		case RINGBUFFER:
			object = this.hzClient.getRingbuffer(id);
			break;
		case SET:
			object = this.hzClient.getSet(id);
			break;
		case LIST:
			object = this.hzClient.getList(id);
			break;
		case MULTI_MAP:
			object = this.hzClient.getMultiMap(id);
			break;
		case REPLICATED_MAP:
			object = this.hzClient.getReplicatedMap(id);
			break;
		case TOPIC:
			object = this.hzClient.getTopic(id);
			break;
		case LOCK:
			object = this.hzClient.getCPSubsystem().getLock(id);
			break;
		case SEMAPHORE:
			object = this.hzClient.getCPSubsystem().getSemaphore(id);
			break;
		case ATOMIC_LONG:
			object = this.hzClient.getCPSubsystem().getAtomicLong(id);
			break;
		case ATOMIC_REFERENCE:
			object = this.hzClient.getCPSubsystem().getAtomicReference(id);
			break;
		case ID_GENERATOR:
			object = this.hzClient.getFlakeIdGenerator(id);
			break;
		case COUNTDOWN_LATCH:
			object = this.hzClient.getCPSubsystem().getCountDownLatch(id);
			break;
		default:
			object = null;
			break;
		}
		return object;
	}

	public Algorithm<R> getAlgorithm(String algorithmId) {
		return this.algorithms.get(algorithmId);
	}

	public Collection<Algorithm<R>> getAllAlgorithms() {
		return this.algorithms.values();
	}

	public String solveAlgorithm(String id, Task initialTask, Integer priority) throws Exception {
		Algorithm<R> alg = new Algorithm<>(this.hzClient, id, priority, initialTask);
		return this.solveAlgorithmAux(id, alg);
	}

	public String solveAlgorithm(String id, Task initialTask, Integer priority, Consumer<R> callback) throws Exception {
		Algorithm<R> alg = new Algorithm<>(this.hzClient, id, priority, initialTask, callback);
		return this.solveAlgorithmAux(id, alg);
	}

	public String solveAlgorithm(String id, Task initialTask, Integer priority, AlgorithmCallback<R> callback)
			throws Exception {
		Algorithm<R> alg = new Algorithm<>(this.hzClient, id, priority, initialTask, callback);
		return this.solveAlgorithmAux(id, alg);
	}

	private String solveAlgorithmAux(String id, Algorithm<R> alg) throws Exception {
		if (this.algorithms.containsKey(id)) {
			throw new Exception("Algorithm with id [" + id + "] already exists");
		}

		String newId = null;

		try {
			this.hzClient.getCPSubsystem().getAtomicLong("added" + id).get();
			this.algorithms.putIfAbsent(id, alg);
		} catch (DistributedObjectDestroyedException e) {
			// The algorithm id was previously used. Since 3.12 destroyed Hazelcast
			// structures cannot be reused again until CP Subsystem is restarted
			boolean inserted = false;
			Algorithm<R> newAlgorithm = null;
			while (!inserted) {
				newId = id + "-" + RandomStringUtils.randomAlphanumeric(5);
				newAlgorithm = new Algorithm<>(this.hzClient, newId, alg);
				inserted = this.algorithms.putIfAbsent(newId, newAlgorithm) == null;
			}
			id = newId;
			alg = newAlgorithm;
			log.warn("Algorithm id was previously used. New algorithm id: {}", id);
		}

		this.taskCompletedEventsCount.putIfAbsent(id, new AtomicLong(0));
		this.taskCompletedLocks.putIfAbsent(alg.getId(), new ReentrantLock());
		this.taskTimeoutLocks.putIfAbsent(alg.getId(), new ReentrantLock());
		this.algorithmStructures.put(alg.getId(), new ConcurrentHashMap<>());
		this.algorithmsMarkedWithTimeout.put(alg.getId(), false);

		IQueue<Task> queue = this.hzClient.getQueue(alg.getId());
		QUEUES.put(alg.getId(), new QueueProperty(alg.getPriority(), System.currentTimeMillis()));

		alg.solve(queue);

		return newId;
	}

	public Map<String, WorkerStats> getWorkers() {
		return this.workers;
	}

	private List<Algorithm<R>> clearAllAlgorithmsFromTermination() {
		List<Algorithm<R>> algs = new ArrayList<>();
		Algorithm<R> alg;
		for (String algorithmId : this.algorithms.keySet()) {
			alg = this.cleanAlgorithmStructures(algorithmId);
			alg.setStatus(Status.TERMINATED);
			if (alg != null) {
				algs.add(alg);
			}
		}
		return algs;
	}

	public List<Algorithm<R>> terminateAlgorithms() {
		this.hzClient.getTopic("stop-algorithms").publish("");
		return this.clearAllAlgorithmsFromTermination();
	}

	public List<Algorithm<R>> blockingTerminateAlgorithms() throws InterruptedException {
		this.terminateBlockingLatch = new CountDownLatch(1);
		timeForTerminate = System.currentTimeMillis();
		this.hzClient.getTopic("stop-algorithms-blocking").publish("");
		this.terminateBlockingLatch.await(12, TimeUnit.SECONDS);
		return this.clearAllAlgorithmsFromTermination();
	}

	public Algorithm<R> blockingTerminateOneAlgorithm(String algorithmId) throws InterruptedException {
		this.terminateOneBlockingLatches.put(algorithmId, new CountDownLatch(1));
		this.hzClient.getTopic("stop-one-algorithm-blocking").publish(algorithmId);
		this.terminateOneBlockingLatches.get(algorithmId).await(12, TimeUnit.SECONDS);
		Algorithm<R> alg = this.cleanAlgorithmStructures(algorithmId);
		if (alg != null) {
			alg.setStatus(Status.TERMINATED);
		}
		return alg;
	}

}
