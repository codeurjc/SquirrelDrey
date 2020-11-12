package es.codeurjc.squirrel.drey.sampleapp;

public class AlgorithmStats {

	String algorithmId;

	int tasksAdded;
	int tasksCompleted;
	int tasksQueued;

	String result;
	long timeOfProcessing;

	int priority;

	public AlgorithmStats(String algorithmId, String result, int tasksAdded, int tasksCompleted, int tasksQueued,
			long timeOfProcessing, int priority) {
		this.algorithmId = algorithmId;
		this.result = result;
		this.tasksAdded = tasksAdded;
		this.tasksCompleted = tasksCompleted;
		this.tasksQueued = tasksQueued;
		this.timeOfProcessing = timeOfProcessing;
		this.priority = priority;
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

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	
}
