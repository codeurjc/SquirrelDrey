package es.codeurjc.squirrel.drey;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.ITopic;

public class Task<T> implements Callable<Void>, Serializable, HazelcastInstanceAware {
	
	protected transient HazelcastInstance hazelcastInstance;
	
	protected String algorithmId;
	protected T result;
	
	protected final int uniqueId = UUID.randomUUID().hashCode();
	
	public int getId() {
		return this.uniqueId;
	}

	public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
		this.hazelcastInstance = hazelcastInstance;
	}
	
	public void setAlgorithm(String algorithmId) {
		this.algorithmId = algorithmId;
	}
	
	public void setResult(T result) {
		this.result = result;
	}
	
	public T getResult() {
		return result;
	}
	
	public void algorithmSolved(T finalResult) {
		ITopic<AlgorithmEvent> topic = hazelcastInstance.getTopic("algorithm-solved");
		topic.publish(new AlgorithmEvent(this.algorithmId, "algorithm-solved", finalResult));
	}
	
	public void process() throws Exception {
		this.call();
	}

	@Override
	public Void call() throws Exception {
		return null;
	}

	public void callback() {
		this.publishQueueStats();
		this.publishCompletedTask();
	}

	protected void publishQueueStats() {
		IQueue<Task<T>> queue = hazelcastInstance.getQueue(this.algorithmId);

		hazelcastInstance.getTopic("queue-stats")
				.publish(new AlgorithmEvent(this.algorithmId, "queue-stats", queue.size()));
	}

	private void publishCompletedTask() {
		hazelcastInstance.getTopic("task-completed").publish(new AlgorithmEvent(this.algorithmId, "task-completed", this));
	}

	protected void addNewTask(Task<?> t) {
		t.setAlgorithm(this.algorithmId);
		IQueue<Task<?>> queue = hazelcastInstance.getQueue(this.algorithmId);
		queue.add(t);
		
		// Update last addition time
		IMap<String, QueueProperty> map = hazelcastInstance.getMap("QUEUES");
		QueueProperty properties = map.get(this.algorithmId);
		properties.lastTimeUpdated.set((int) System.currentTimeMillis());
		map.set(this.algorithmId, properties);
	}
	
	@Override
	public int hashCode() {
		return this.uniqueId;
	}
	
	@Override
	public boolean equals(Object o) {
		return (this.uniqueId == ((Task<T>)o).uniqueId);
	}
}
