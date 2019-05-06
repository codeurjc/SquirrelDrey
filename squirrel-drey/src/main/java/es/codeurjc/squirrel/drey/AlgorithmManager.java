package es.codeurjc.squirrel.drey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;

import es.codeurjc.squirrel.drey.Algorithm.Status;

/**
 * @author Pablo Fuente (pablo.fuente@urjc.es)
 */
public class AlgorithmManager<R> {

	private static final Logger log = LoggerFactory.getLogger(AlgorithmManager.class);

	HazelcastInstance hzClient;
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

	public AlgorithmManager(String HAZELCAST_CLIENT_CONFIG, boolean withAWSCloudWatch) {

		boolean developmentMode = System.getProperty("devmode") != null ? Boolean.valueOf(System.getProperty("devmode")) : false;

		if (developmentMode) {
			Worker.launch();
		}

		ClientConfig config = new ClientConfig();
		try {
			config = new XmlClientConfigBuilder(HAZELCAST_CLIENT_CONFIG).build();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.hzClient = HazelcastClient.newHazelcastClient(config);

		this.hzClient.getCluster().addMembershipListener(new ClusterMembershipListener(this));

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
		if (this.withAWSCloudWatch) this.cloudWatchModule = new CloudWatchModule(this.hzClient, this.QUEUES);

		hzClient.getTopic("task-completed").addMessageListener((message) -> {
			AlgorithmEvent ev = (AlgorithmEvent) message.getMessageObject();
			Task t = (Task) ev.getContent();
			log.info("TASK [{}] completed for algorithm [{}]. Took {} ms", t, ev.getAlgorithmId(), System.currentTimeMillis() - t.getTimeStarted());
			Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());
			
			hzClient.getAtomicLong("completed" + alg.getId()).incrementAndGet();

			ReentrantLock l = this.taskCompletedLocks.get(ev.getAlgorithmId());
			l.lock();
			try {
				if (alg == null) {
					// Interruption of algorithm. This task is being terminated after stopped
					log.info("TASK {} COMPLETED FOR STOPPED ALGORITHM {}: ", t, ev.getAlgorithmId());
					this.cleanAlgorithmStructures(ev.getAlgorithmId());
				} else {

					algorithmStructures.get(alg.getId()).putAll(t.getHazelcastStructures());
					if (t.getFinalResult() != null) alg.setResult((R) t.getFinalResult());

					if (alg.hasSuccessullyFinished(this.taskCompletedEventsCount.get(ev.getAlgorithmId()).incrementAndGet())) {
						log.info("ALGORITHM SOLVED: Algorithm: {}, Result: {}, Last task: {}", ev.getAlgorithmId(), t.getFinalResult(), t);
						try {
							alg.runCallbackSuccess();
						} catch (Exception e) {
							log.error(e.getMessage());
						}

						cleanAlgorithmStructures(alg.getId());
					} else if (this.algorithmsMarkedWithTimeout.get(alg.getId())) {
						log.warn("ALGORITHM TIMEOUT: Algorithm: {}, Completed task: {}", ev.getAlgorithmId(), t);
						final Integer runningAndFinishedTasks = this.algorithmsRunningAndFinishedTasksOnTimeout.get(alg.getId());
						if (alg.hasFinishedRunningTasks(runningAndFinishedTasks)) {
							try {
								log.warn("Last running task [{}] in algorithm [{}]. Starting algorithm termination", t, alg.getId());
								this.blockingTerminateOneAlgorithm(alg.getId());
							} catch (InterruptedException e) {
								log.error("Error while forcibly terminating algorithm [{}] for task [{}] triggering timeout: {}", alg.getId(), t, e.getMessage());
							}
							alg.runCallbackError(Status.TIMEOUT);
						} else {
							log.warn("There are still running tasks in algorithm [{}]. Last running task will trigger algorithm termination by timeout", alg.getId());
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
			
			hzClient.getAtomicLong("timeout" + alg.getId()).incrementAndGet();

			ReentrantLock l = this.taskTimeoutLocks.get(ev.getAlgorithmId());
			l.lock();
			try {
				
				int runningAndFinishedTasks;
				final Integer previouslyStoredTasks = this.algorithmsRunningAndFinishedTasksOnTimeout.get(alg.getId());
				if (previouslyStoredTasks == null) {
					// First timeout task of the algorithm
					runningAndFinishedTasks = alg.getTasksAdded() - alg.getTasksQueued();
					log.info("Algorithm [{}] has {} running tasks when termination timeout caused by task [{}]", alg.getId(), runningAndFinishedTasks - alg.getTasksCompleted(), t);
					
					this.algorithmsRunningAndFinishedTasksOnTimeout.put(alg.getId(), runningAndFinishedTasks);
					IQueue<Task> queue = this.hzClient.getQueue(alg.getId());
					queue.clear();
					queue.destroy();
					
					log.info("Task queue for algorithm [{}] has been emptied because of timeout termination caused by task [{}]", alg.getId(), t);
				} else {
					// Other timeout tasks
					runningAndFinishedTasks = previouslyStoredTasks;
				}
				
				if (alg.hasFinishedRunningTasks(runningAndFinishedTasks)) {
					try {
						log.warn("Last running task [{}] in algorithm [{}]. Starting algorithm termination", t, alg.getId());
						this.blockingTerminateOneAlgorithm(alg.getId());
					} catch (InterruptedException e) {
						log.error("Error while forcibly terminating algorithm [{}] for task [{}] triggering timeout: {}", alg.getId(), t, e.getMessage());
					}
					alg.runCallbackError(Status.TIMEOUT);
				} else {
					log.warn("There are still running tasks in algorithm [{}]. Last running task will trigger algorithm termination by timeout", alg.getId());
					this.algorithmsMarkedWithTimeout.put(alg.getId(), true);
				}
			} finally {
				l.unlock();
			}
		});
		hzClient.getTopic("stop-algorithms-done").addMessageListener((message) -> {
			log.info("Algorithms succesfully terminated on {} milliseconds",
					System.currentTimeMillis() - this.timeForTerminate);
			this.terminateBlockingLatch.countDown();
		});
		hzClient.getTopic("stop-one-algorithm-done").addMessageListener((message) -> {
			log.info("Algorithm [{}] succesfully terminated", message.getMessageObject());
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

		this.hzClient.getAtomicLong("added" + algorithmId).destroy();
		this.hzClient.getAtomicLong("completed" + algorithmId).destroy();
		this.hzClient.getAtomicLong("timeout" + algorithmId).destroy();

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
			object = this.hzClient.getLock(id);
			break;
		case SEMAPHORE:
			object = this.hzClient.getSemaphore(id);
			break;
		case ATOMIC_LONG:
			object = this.hzClient.getAtomicLong(id);
			break;
		case ATOMIC_REFERENCE:
			object = this.hzClient.getAtomicReference(id);
			break;
		case ID_GENERATOR:
			object = this.hzClient.getIdGenerator(id);
			break;
		case COUNTDOWN_LATCH:
			object = this.hzClient.getCountDownLatch(id);
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

	public void solveAlgorithm(String id, Task initialTask, Integer priority) throws Exception {
		Algorithm<R> alg = new Algorithm<>(this.hzClient, id, priority, initialTask);
		this.solveAlgorithmAux(id, alg);
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority, Consumer<R> callback) throws Exception {
		Algorithm<R> alg = new Algorithm<>(this.hzClient, id, priority, initialTask, callback);
		this.solveAlgorithmAux(id, alg);
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority, AlgorithmCallback<R> callback)
			throws Exception {
		Algorithm<R> alg = new Algorithm<>(this.hzClient, id, priority, initialTask, callback);
		this.solveAlgorithmAux(id, alg);
	}

	private void solveAlgorithmAux(String id, Algorithm<R> alg) throws Exception {
		if (this.algorithms.putIfAbsent(id, alg) != null) {
			throw new Exception("Algorithm with id [" + id + "] already exists");
		}
		this.taskCompletedEventsCount.putIfAbsent(id, new AtomicLong(0));
		this.taskCompletedLocks.putIfAbsent(alg.getId(), new ReentrantLock());
		this.taskTimeoutLocks.putIfAbsent(alg.getId(), new ReentrantLock());
		this.algorithmStructures.put(alg.getId(), new ConcurrentHashMap<>());
		this.algorithmsMarkedWithTimeout.put(alg.getId(), false);

		IQueue<Task> queue = this.hzClient.getQueue(alg.getId());
		QUEUES.put(alg.getId(), new QueueProperty(alg.getPriority(), System.currentTimeMillis()));

		alg.solve(queue);
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
