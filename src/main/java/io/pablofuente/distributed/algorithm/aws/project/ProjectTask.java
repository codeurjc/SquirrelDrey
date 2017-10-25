package io.pablofuente.distributed.algorithm.aws.project;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
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
		this.project.incrementTasksCompleted();
		this.publishProjectStats();
		return null;
	}
	
	protected void publishProjectStats() {
		IQueue<ProjectTask> queue = hazelcastInstance.getQueue(this.project.getId());
		
		List<Integer> l = new ArrayList<>();
		l.add(0, queue.size());
		l.add(1, this.project.getTasksCompleted());
		
		hazelcastInstance.getTopic("queue-stats")
				.publish(new MyEvent(this.project.getId(), "queue-stats", l));
	}

}
