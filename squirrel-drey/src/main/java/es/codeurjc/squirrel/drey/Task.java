package es.codeurjc.squirrel.drey;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;

public class Task implements Callable<Void>, Serializable, HazelcastInstanceAware {
	
	private static final long serialVersionUID = 1L;

	protected transient HazelcastInstance hazelcastInstance;
	
	protected String algorithmId;
	
	protected final int uniqueId = UUID.randomUUID().hashCode();
	
	private Object finalResult = null;
	
	public int getId() {
		return this.uniqueId;
	}

	public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
		this.hazelcastInstance = hazelcastInstance;
	}
	
	public void setAlgorithm(String algorithmId) {
		this.algorithmId = algorithmId;
	}
	
	public void algorithmSolved(Object finalResult) {
		this.finalResult = finalResult;
	}
	
	public Object getFinalResult() {
		return this.finalResult;
	}
	
	public void process() throws Exception {
		this.call();
	}

	@Override
	public Void call() throws Exception {
		return null;
	}

	public void callback() {
		this.publishTasksQueued();
		hazelcastInstance.getTopic("task-completed").publish(new AlgorithmEvent(this.algorithmId, "task-completed", this));
	}

	protected void addNewTask(Task t) {
		t.setAlgorithm(this.algorithmId);
		IQueue<Task> queue = hazelcastInstance.getQueue(this.algorithmId);
		queue.add(t);
		
		hazelcastInstance.getTopic("task-added").publish(new AlgorithmEvent(this.algorithmId, "task-added", t));
		
		// Update last addition time
		IMap<String, QueueProperty> map = hazelcastInstance.getMap("QUEUES");
		QueueProperty properties = map.get(this.algorithmId);
		properties.lastTimeUpdated.set((int) System.currentTimeMillis());
		map.set(this.algorithmId, properties);
	}
	
	protected void publishTasksQueued() {
		IQueue<Task> queue = hazelcastInstance.getQueue(this.algorithmId);

		hazelcastInstance.getTopic("tasks-queued")
				.publish(new AlgorithmEvent(this.algorithmId, "tasks-queued", queue.size()));
	}
	
	@Override
	public int hashCode() {
		return this.uniqueId;
	}
	
	@Override
	public boolean equals(Object o) {
		return (this.uniqueId == ((Task)o).uniqueId);
	}
}
