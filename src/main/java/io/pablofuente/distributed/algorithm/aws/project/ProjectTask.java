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
		return null;
	}

	public String callback() {
		return null;
	}
	
	protected void publishQueueStats() {
		IQueue<ProjectTask> queue = hazelcastInstance.getQueue(this.project.getId());
		hazelcastInstance.getTopic("executor-stats")
				.publish(new MyEvent(this.project.getId(), "executor-stats", queue.size()));
	}

}
