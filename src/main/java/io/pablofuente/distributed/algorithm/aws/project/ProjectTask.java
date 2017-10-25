package io.pablofuente.distributed.algorithm.aws.project;

import java.io.Serializable;
import java.util.concurrent.Callable;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IQueue;

public class ProjectTask implements Callable<String>, Serializable, HazelcastInstanceAware {

	private static final long serialVersionUID = 1L;
	protected Project project;
	protected transient HazelcastInstance hazelcastInstance;

	public ProjectTask(Project project) {
		this.project = project;
	}

	public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
		this.hazelcastInstance = hazelcastInstance;
	}

	@Override
	public String call() throws Exception {
		this.publishQueueStats();
		return null;
	}

	public String callback() {
		this.publishQueueStats();
		this.publishCompletedTask();
		return null;
	}

	protected void publishQueueStats() {
		IQueue<ProjectTask> queue = hazelcastInstance.getQueue(this.project.getId());

		hazelcastInstance.getTopic("queue-stats")
				.publish(new MyEvent(this.project.getId(), "queue-stats", queue.size()));
	}

	protected void publishCompletedTask() {
		hazelcastInstance.getTopic("task-completed").publish(new MyEvent(this.project.getId(), "task-completed", this));
	}

}
