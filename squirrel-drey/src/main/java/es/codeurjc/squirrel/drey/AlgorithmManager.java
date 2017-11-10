package es.codeurjc.squirrel.drey;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;

public class AlgorithmManager<R> {
	
	private static final Logger log = LoggerFactory.getLogger(AlgorithmManager.class);
	
	HazelcastInstance hzClient;
	Map<String, Algorithm<R>> algorithms;
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
		this.workers = new ConcurrentHashMap<>();
		
		this.QUEUES = this.hzClient.getMap("QUEUES");
		
		this.terminateOneBlockingLatches = new ConcurrentHashMap<>();
		
		this.withAWSCloudWatch = withAWSCloudWatch;
		if (this.withAWSCloudWatch) this.cloudWatchModule = new CloudWatchModule(this.hzClient, this.QUEUES);

		hzClient.getTopic("task-added").addMessageListener((message) -> {
			AlgorithmEvent ev = (AlgorithmEvent) message.getMessageObject();
			log.info("TASK [{}] added for algorithm [{}]", ev.getContent(), ev.getAlgorithmId());
			Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());
			alg.incrementTasksAdded();
		});
		hzClient.getTopic("task-completed").addMessageListener((message) -> {
			AlgorithmEvent ev = (AlgorithmEvent) message.getMessageObject();
			Task t = (Task) ev.getContent();
			log.info("TASK [{}] completed for algorithm [{}]", t, ev.getAlgorithmId());
			Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());
			alg.incrementTasksCompleted();
			
			if (alg.hasFinished()) {
				log.info("ALGORITHM SOLVED: Algorithm: " + ev.getAlgorithmId() + ", Result: " + t.getFinalResult());
				alg.setFinishTime(System.currentTimeMillis());
				try {
					if (t.getFinalResult() != null)	alg.setResult((R) t.getFinalResult());
					alg.runCallback();
				} catch(Exception e) {
					log.error(e.getMessage());
				}
				// Remove algorithm
				this.algorithms.remove(ev.getAlgorithmId());
				// Remove distributed queue
				this.QUEUES.remove(ev.getAlgorithmId());
			}
		});
		hzClient.getTopic("tasks-queued").addMessageListener((message) -> {
			AlgorithmEvent ev = (AlgorithmEvent) message.getMessageObject();
			log.info("EXECUTOR STATS for queue [{}]: Tasks waiting in queue -> {}", ev.getAlgorithmId(), ev.getContent());
			Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());
			alg.setTasksQueued((int) ev.getContent());
		});
		hzClient.getTopic("stop-algorithms-done").addMessageListener((message) -> {
			log.info("Algorithms succesfully terminated on {} milliseconds", System.currentTimeMillis() - this.timeForTerminate);
			this.terminateBlockingLatch.countDown();
		});
		hzClient.getTopic("stop-one-algorithm-done").addMessageListener((message) -> {
			log.info("Algorithm [{}] succesfully terminated", message.getMessageObject());
			this.terminateOneBlockingLatches.get((String)message.getMessageObject()).countDown();
		});
		hzClient.getTopic("worker-stats").addMessageListener((message) -> {
			WorkerEvent ev = (WorkerEvent) message.getMessageObject();
			log.info("WORKER EVENT for worker [{}]: {}", ev.getWorkerId(), ev.getContent());
			this.workers.put(ev.getWorkerId(), (WorkerStats) ev.getContent());
		});
	}

	public Algorithm<R> getAlgorithm(String algorithmId) {
		return this.algorithms.get(algorithmId);
	}
	
	public Algorithm<R> removeAlgorithm(String algorithmId) {
		return this.algorithms.remove(algorithmId);
	}
	
	public void clearAlgorithms() {
		this.algorithms.clear();
	}
	
	public void solveAlgorithm(String id, Task initialTask, Integer priority) throws Exception {
		Algorithm<R> alg = new Algorithm<>(id, priority, initialTask);
		
		if (this.algorithms.putIfAbsent(id, alg) != null) {
			throw new Exception("Algorithm with id [" + id + "] already exists");
		}
		
		IQueue<Task> queue = this.hzClient.getQueue(alg.getId());
		QUEUES.put(alg.getId(), new QueueProperty(alg.getPriority(), new AtomicInteger((int) System.currentTimeMillis())));
		
		alg.solve(queue);
		hzClient.getTopic("task-added").publish(new AlgorithmEvent(alg.getId(), "task-added", alg.getInitialTask()));
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority, Consumer<R> callback) throws Exception {
		Algorithm<R> alg = new Algorithm<>(id, priority, initialTask, callback);
		
		if (this.algorithms.putIfAbsent(id, alg) != null) {
			throw new Exception("Algorithm with id [" + id + "] already exists");
		}
		
		IQueue<Task> queue = this.hzClient.getQueue(alg.getId());
		QUEUES.put(alg.getId(), new QueueProperty(alg.getPriority(), new AtomicInteger((int) System.currentTimeMillis())));
		
		alg.solve(queue);
		hzClient.getTopic("task-added").publish(new AlgorithmEvent(alg.getId(), "task-added", alg.getInitialTask()));
	}
	
	public Map<String, WorkerStats> getWorkers() {
		return this.workers;
	}
	
	public void terminateAlgorithms() {
		this.hzClient.getTopic("stop-algorithms").publish("");
		this.clearAlgorithms();
	}
	
	public void blockingTerminateAlgorithms() throws InterruptedException {
		this.terminateBlockingLatch = new CountDownLatch(1);
		timeForTerminate = System.currentTimeMillis();
		this.hzClient.getTopic("stop-algorithms-blocking").publish("");
		this.terminateBlockingLatch.await(12, TimeUnit.SECONDS);
		this.clearAlgorithms();
	}
	
	public void blockingTerminateOneAlgorithm(String algorithmId) throws InterruptedException {
		this.terminateOneBlockingLatches.put(algorithmId, new CountDownLatch(1));
		this.hzClient.getTopic("stop-one-algorithm-blocking").publish(algorithmId);
		this.terminateOneBlockingLatches.get(algorithmId).await(12, TimeUnit.SECONDS);
		this.removeAlgorithm(algorithmId);
	}

}
