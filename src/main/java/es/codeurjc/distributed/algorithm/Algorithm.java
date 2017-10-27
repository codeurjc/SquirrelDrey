package es.codeurjc.distributed.algorithm;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import com.hazelcast.core.IQueue;

/**
 * @author Pablo Fuente (pablo.fuente@urjc.es)
 */
public class Algorithm<R> implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;
	private Integer priority;
	
	private R result;
	
	private Integer tasksQueued;
	private AtomicInteger tasksCompleted;
	private Long initTime;
	private Long finishTime;

	private Task<?> initialTask;

	public Algorithm(String id, Integer priority, Task<?> initialTask) {
		this.id = id;
		this.priority = priority;
		this.tasksCompleted = new AtomicInteger(0);
		
		initialTask.setAlgorithm(this);
		this.initialTask = initialTask;
	}
	
	public String getId() {
		return this.id;
	}
	
	public Integer getPriority() {
		return this.priority;
	}

	public void solve(IQueue<Task<?>> queue) throws Exception {
		this.initTime = System.currentTimeMillis();
		queue.add(this.initialTask);
	}

	public Integer getTasksQueued() {
		return this.tasksQueued;
	}

	public void setTasksQueued(int tasksQueued) {
		this.tasksQueued = tasksQueued;
	}
	
	public int getTasksCompleted() {
		return this.tasksCompleted.get();
	}

	public void setTasksCompleted(int tasksCompleted) {
		this.tasksCompleted.set(tasksCompleted);
	}
	
	public synchronized void incrementTasksCompleted() {
		this.tasksCompleted.incrementAndGet();
	}

	public R getResult() {
		return result;
	}

	public void setResult(R result) {
		this.result = result;
	}

	public void setFinishTime(long finishTime) {
		this.finishTime = finishTime;
	}

	public Long getTimeOfProcessing() {
		if (this.finishTime != null) {
			return (this.finishTime - this.initTime) / 1000;
		} else {
			return 0L;
		}
	}

}
