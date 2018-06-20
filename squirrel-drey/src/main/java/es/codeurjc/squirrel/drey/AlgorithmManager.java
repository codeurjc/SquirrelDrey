package es.codeurjc.squirrel.drey;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

/**
 * @author Pablo Fuente (pablo.fuente@urjc.es)
 */
public class AlgorithmManager<R> {

	private static final Logger log = LoggerFactory.getLogger(AlgorithmManager.class);

	HazelcastInstance hzClient;
	Map<String, Algorithm<R>> algorithms;
	Map<String, AtomicLong> taskCompletedEventsCount;
	Map<String, ReentrantLock> taskCompletedLocks;
	Map<String, Map<String, String>> algorithmStructures;
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
		this.algorithmStructures = new ConcurrentHashMap<>();
		this.workers = new ConcurrentHashMap<>();

		this.QUEUES = this.hzClient.getMap("QUEUES");

		this.terminateOneBlockingLatches = new ConcurrentHashMap<>();

		this.withAWSCloudWatch = withAWSCloudWatch;
		if (this.withAWSCloudWatch) this.cloudWatchModule = new CloudWatchModule(this.hzClient, this.QUEUES);

		/*
		 * hzClient.getTopic("task-added").addMessageListener((message) -> {
		 * AlgorithmEvent ev = (AlgorithmEvent) message.getMessageObject();
		 * log.info("TASK [{}] added for algorithm [{}]", ev.getContent(),
		 * ev.getAlgorithmId()); Algorithm<R> alg =
		 * this.algorithms.get(ev.getAlgorithmId()); alg.incrementTasksAdded(); });
		 */
		hzClient.getTopic("task-completed").addMessageListener((message) -> {
			AlgorithmEvent ev = (AlgorithmEvent) message.getMessageObject();
			Task t = (Task) ev.getContent();
			log.info("TASK [{}] completed for algorithm [{}]", t, ev.getAlgorithmId());
			Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());

			this.taskCompletedLocks.get(ev.getAlgorithmId()).lock();
			try {
				if (alg == null) {
					// Interruption of algorithm. This task is being terminated after stopped
					log.info("TASK {} COMPLETED FOR STOPPED ALGORITHM {}: ", t, ev.getAlgorithmId());
					this.cleanAlgorithmStructures(ev.getAlgorithmId());
				} else {

					algorithmStructures.get(alg.getId()).putAll(t.getHazelcastStructures());
					if (t.getFinalResult() != null) alg.setResult((R) t.getFinalResult());

					if (alg.hasFinished(t, this.taskCompletedEventsCount.get(ev.getAlgorithmId()).incrementAndGet())) {
						log.info("ALGORITHM SOLVED: Algorithm: {}, Result: {}, Last task: {}", ev.getAlgorithmId(), t.getFinalResult(), t);
						alg.setFinishTime(System.currentTimeMillis());
						try {
							alg.runCallback();
						} catch (Exception e) {
							log.error(e.getMessage());
						}

						cleanAlgorithmStructures(alg.getId());
					}
				}
			} finally {
				this.taskCompletedLocks.get(ev.getAlgorithmId()).unlock();
			}
		});
		/*
		 * hzClient.getTopic("tasks-queued").addMessageListener((message) -> {
		 * AlgorithmEvent ev = (AlgorithmEvent) message.getMessageObject(); int n =
		 * this.hzClient.getQueue(ev.getAlgorithmId()).size();
		 * log.info("EXECUTOR STATS for queue [{}]: Tasks waiting in queue -> {}",
		 * ev.getAlgorithmId(), n); Algorithm<R> alg =
		 * this.algorithms.get(ev.getAlgorithmId()); alg.setTasksQueued(n); });
		 */
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
			log.info("WORKER EVENT for worker [{}]: {}", ev.getWorkerId(), ev.getContent());
			this.workers.put(ev.getWorkerId(), (WorkerStats) ev.getContent());
		});
	}

	private void cleanAlgorithmStructures(String algorithmId) {

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

		// Remove algorithm
		this.algorithms.remove(algorithmId);
		// Remove the count of task completed events
		this.taskCompletedEventsCount.remove(algorithmId);
		// Remove the lock for this algorithm
		this.taskCompletedLocks.remove(algorithmId);
		// Remove algorithm distributed structures
		this.algorithmStructures.remove(algorithmId);
		// Remove distributed task queue
		this.QUEUES.remove(algorithmId);
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
		/*
		 * case CARDINALITY_ESTIMATOR: object =
		 * this.hzClient.getCardinalityEstimator(id); break;
		 */
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

		if (this.algorithms.putIfAbsent(id, alg) != null) {
			throw new Exception("Algorithm with id [" + id + "] already exists");
		}
		this.taskCompletedEventsCount.putIfAbsent(alg.getId(), new AtomicLong(0));
		this.taskCompletedLocks.putIfAbsent(alg.getId(), new ReentrantLock());
		this.algorithmStructures.put(alg.getId(), new ConcurrentHashMap<>());

		IQueue<Task> queue = this.hzClient.getQueue(alg.getId());
		QUEUES.put(alg.getId(), new QueueProperty(alg.getPriority(), System.currentTimeMillis()));

		alg.solve(queue);
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority, Consumer<R> callback) throws Exception {
		Algorithm<R> alg = new Algorithm<>(this.hzClient, id, priority, initialTask, callback);

		if (this.algorithms.putIfAbsent(id, alg) != null) {
			throw new Exception("Algorithm with id [" + id + "] already exists");
		}
		this.taskCompletedEventsCount.putIfAbsent(id, new AtomicLong(0));
		this.taskCompletedLocks.putIfAbsent(alg.getId(), new ReentrantLock());
		this.algorithmStructures.put(alg.getId(), new ConcurrentHashMap<>());

		IQueue<Task> queue = this.hzClient.getQueue(alg.getId());
		QUEUES.put(alg.getId(), new QueueProperty(alg.getPriority(), System.currentTimeMillis()));

		alg.solve(queue);
	}

	public Map<String, WorkerStats> getWorkers() {
		return this.workers;
	}

	private void clearAllAlgorithms() {
		for (String algorithmId : this.algorithms.keySet()) {
			this.cleanAlgorithmStructures(algorithmId);
		}
	}

	public void terminateAlgorithms() {
		this.hzClient.getTopic("stop-algorithms").publish("");
		this.clearAllAlgorithms();
	}

	public void blockingTerminateAlgorithms() throws InterruptedException {
		this.terminateBlockingLatch = new CountDownLatch(1);
		timeForTerminate = System.currentTimeMillis();
		this.hzClient.getTopic("stop-algorithms-blocking").publish("");
		this.terminateBlockingLatch.await(12, TimeUnit.SECONDS);
		this.clearAllAlgorithms();
	}

	public void blockingTerminateOneAlgorithm(String algorithmId) throws InterruptedException {
		this.terminateOneBlockingLatches.put(algorithmId, new CountDownLatch(1));
		this.hzClient.getTopic("stop-one-algorithm-blocking").publish(algorithmId);
		this.terminateOneBlockingLatches.get(algorithmId).await(12, TimeUnit.SECONDS);
		this.cleanAlgorithmStructures(algorithmId);
	}

}
