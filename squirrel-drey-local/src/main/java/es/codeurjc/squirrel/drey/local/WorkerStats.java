package es.codeurjc.squirrel.drey.local;

import java.io.Serializable;

public class WorkerStats implements Serializable {

	private static final long serialVersionUID = 1L;

	String workerId;
	Integer totalCores;
	Integer workingCores;
	long tasksAdded;
	long totalCompletedTasks;

	public WorkerStats(String workerId, int totalCores, int workingCores, long tasksAdded, long totalCompletedTasks) {
		this.workerId = workerId;
		this.totalCores = totalCores;
		this.workingCores = workingCores;
		this.tasksAdded = tasksAdded;
		this.totalCompletedTasks = totalCompletedTasks;
	}

	public WorkerStats() {
	}

	public String getWorkerId() {
		return this.workerId;
	}

	public void setWorkerId(String workerId) {
		this.workerId = workerId;
	}

	public Integer getTotalCores() {
		return totalCores;
	}

	public void setTotalCores(Integer totalCores) {
		this.totalCores = totalCores;
	}

	public Integer getWorkingCores() {
		return workingCores;
	}

	public void setWorkingCores(Integer workingCores) {
		this.workingCores = workingCores;
	}

	public long getTasksAdded() {
		return tasksAdded;
	}

	public void setTasksAdded(long tasksAdded) {
		this.tasksAdded = tasksAdded;
	}

	public long getTotalCompletedTasks() {
		return totalCompletedTasks;
	}

	public void setTotalCompletedTasks(long totalCompletedTasks) {
		this.totalCompletedTasks = totalCompletedTasks;
	}

	@Override
	public String toString() {
		return "[workerId: " + this.workerId + ", totalCores: " + this.totalCores + ", workingCores: "
				+ this.workingCores + ", tasksAdded: " + this.tasksAdded + ", totalCompletedTasks: "
				+ this.totalCompletedTasks + "]";
	}

}
