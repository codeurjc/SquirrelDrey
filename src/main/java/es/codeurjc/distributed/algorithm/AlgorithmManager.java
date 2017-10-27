package es.codeurjc.distributed.algorithm;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;

import es.codeurjc.sampleapp.App;

public class AlgorithmManager<R> {
	
	private HazelcastInstance hzClient;
	private Map<String, Algorithm<R>> algorithms;
	private IMap<String, Integer> QUEUES;
		
	public AlgorithmManager(String HAZELCAST_CLIENT_CONFIG) {
		
		ClientConfig config = new ClientConfig();
		try {
			config = new XmlClientConfigBuilder(HAZELCAST_CLIENT_CONFIG).build();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.hzClient = HazelcastClient.newHazelcastClient(config);
		
		this.hzClient.getCluster().addMembershipListener(new ClusterMembershipListener());

		this.algorithms = new ConcurrentHashMap<>();
		this.QUEUES = this.hzClient.getMap("QUEUES");

		hzClient.getTopic("algorithm-solved").addMessageListener((message) -> {
			MyEvent ev = (MyEvent) message.getMessageObject();
			App.logger.info("ALGORITHM SOLVED: Algorithm: " + ev.getAlgorithmId() + ", Result: " + ev.getContent());
			Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());
			alg.setFinishTime(System.currentTimeMillis());
			try {
				alg.setResult((R) ev.getContent());
				alg.runCallback();
			} catch(Exception e) {
				App.logger.error(e.getMessage());
			}
			
			// Remove distributed queue
			this.QUEUES.remove(ev.getAlgorithmId());
		});
		hzClient.getTopic("queue-stats").addMessageListener((message) -> {
			MyEvent ev = (MyEvent) message.getMessageObject();
			App.logger.info("EXECUTOR STATS for queue [" + ev.getAlgorithmId() + "]: Tasks waiting in queue -> "
					+ ev.getContent());
			Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());
			alg.setTasksQueued((int) ev.getContent());
		});
		hzClient.getTopic("task-completed").addMessageListener((message) -> {
			MyEvent ev = (MyEvent) message.getMessageObject();
			App.logger.info("TASK [" + ev.getContent() + "] completed for algorithm [" + ev.getAlgorithmId() + "] with result [" + ((Task<?>)ev.getContent()).getResult() + "]");
			Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());
			alg.incrementTasksCompleted();
		});
	}

	public Algorithm<R> getAlgorithm(String algorithmId) {
		return this.algorithms.get(algorithmId);
	}
	
	public void solveAlgorithm(String id, Task<?> initialTask, Integer priority) throws Exception {
		Algorithm<R> alg = new Algorithm<>(id, priority, initialTask);
		this.algorithms.put(id, alg);
		
		IQueue<Task<?>> queue = this.hzClient.getQueue(alg.getId());
		QUEUES.put(alg.getId(), alg.getPriority());
		
		alg.solve(queue);
	}

	public void solveAlgorithm(String id, Task<?> initialTask, Integer priority, Consumer<R> callback) throws Exception {
		Algorithm<R> alg = new Algorithm<>(id, priority, initialTask, callback);
		this.algorithms.put(id, alg);
		
		IQueue<Task<?>> queue = this.hzClient.getQueue(alg.getId());
		QUEUES.put(alg.getId(), alg.getPriority());
		
		alg.solve(queue);
	}

}
