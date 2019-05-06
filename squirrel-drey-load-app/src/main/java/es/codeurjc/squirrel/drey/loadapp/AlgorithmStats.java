package es.codeurjc.squirrel.drey.loadapp;

import es.codeurjc.squirrel.drey.Algorithm.Status;

public class AlgorithmStats {

	String algorithmId;

	int tasksAdded;
	int tasksCompleted;
	int tasksQueued;

	String result;
	Status status;
	long timeOfProcessing;

	public AlgorithmStats(String algorithmId, String result, Status status, int tasksAdded, int tasksCompleted,
			int tasksQueued, long timeOfProcessing) {
		this.algorithmId = algorithmId;
		this.result = result;
		this.status = status;
		this.tasksAdded = tasksAdded;
		this.tasksCompleted = tasksCompleted;
		this.tasksQueued = tasksQueued;
		this.timeOfProcessing = timeOfProcessing;
	}

	public String getAlgorithmId() {
		return this.algorithmId;
	}

	public void setAlgorithmId(String algorithmId) {
		this.algorithmId = algorithmId;
	}

	public int getTasksAdded() {
		return tasksAdded;
	}

	public int getTasksCompleted() {
		return tasksCompleted;
	}

	public int getTasksQueued() {
		return tasksQueued;
	}

	public void setTasksAdded(int tasksAdded) {
		this.tasksAdded = tasksAdded;
	}

	public void setTasksCompleted(int tasksCompleted) {
		this.tasksCompleted = tasksCompleted;
	}

	public void setTasksQueued(int tasksQueued) {
		this.tasksQueued = tasksQueued;
	}

	public long getTimeOfProcessing() {
		return timeOfProcessing;
	}

	public void setTimeOfProcessing(long timeOfProcessing) {
		this.timeOfProcessing = timeOfProcessing;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

}
