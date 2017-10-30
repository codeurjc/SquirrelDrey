package es.codeurjc.distributed.algorithm;

import java.io.Serializable;
import java.util.concurrent.Callable;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.ITopic;

public class Task<T> implements Callable<Void>, Serializable, HazelcastInstanceAware {

	private static final long serialVersionUID = 1L;
	
	protected transient HazelcastInstance hazelcastInstance;
	
	protected String algorithmId;
	protected T result;

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
		ITopic<MyEvent> topic = hazelcastInstance.getTopic("algorithm-solved");
		topic.publish(new MyEvent(this.algorithmId, "algorithm-solved", finalResult));
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
				.publish(new MyEvent(this.algorithmId, "queue-stats", queue.size()));
	}

	private void publishCompletedTask() {
		hazelcastInstance.getTopic("task-completed").publish(new MyEvent(this.algorithmId, "task-completed", this));
	}

	protected void addNewTask(Task<?> t) {
		t.setAlgorithm(this.algorithmId);
		IQueue<Task<?>> queue = hazelcastInstance.getQueue(this.algorithmId);
		queue.add(t);		
	}
}
