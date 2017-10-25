package io.pablofuente.distributed.algorithm.aws.project;

import java.io.Serializable;

import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IQueue;

/**
 * Each project has 3 different types of tasks:
 * 
 * 1) Preparation task: generates all the atomic tasks, performing the necessary
 * data processing
 * 
 * 2) Atomic task: performs one abstract atomic operation
 * 
 * 3) Solve task: gathers all the results and calculates the final value with
 * all of them
 * 
 * To initialize the resolution of the project, only preparationTask must be
 * called. preparationTask will call all atomicTasks. Last atomicTask will call
 * solveTask, which finally returns the result
 * 
 * @author Pablo Fuente (pablo.fuente@urjc.es)
 */
public class Project implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;
	private String data;
	private String result;
	private Integer tasksQueued;
	private Long initTime;
	private Long finishTime;

	private ProjectPreparationTask preparationTask;

	public Project(String id, String data) {
		this.id = id;
		this.data = data;
		this.preparationTask = new ProjectPreparationTask(this);
	}

	public String getId() {
		return this.id;
	}

	public String getData() {
		return this.data;
	}

	public void solve(IQueue<ProjectTask> queue, IAtomicLong atomicLong) throws Exception {
		this.preparationTask.setAtomicLong(atomicLong);
		this.initTime = System.currentTimeMillis();
		queue.add(this.preparationTask);
	}

	public Integer getTasksQueued() {
		return this.tasksQueued;
	}

	public void setTasksQueued(int tasksQueued) {
		this.tasksQueued = tasksQueued;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
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
