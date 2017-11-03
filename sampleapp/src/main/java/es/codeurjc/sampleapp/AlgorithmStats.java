package es.codeurjc.sampleapp;

public class AlgorithmStats {
	
	int taskQueued;
	int taskCompleted;
	String result;
	long timeOfProcessing;
	
	public AlgorithmStats(String result, int taskQueued, int taskCompleted, long timeOfProcessing) {
		this.result = result;
		this.taskQueued = taskQueued;
		this.taskCompleted = taskCompleted;
		this.timeOfProcessing = timeOfProcessing;
	}
	
	public String getResult() {
		return result;
	}
	
	public int getTaskQueued() {
		return taskQueued;
	}
	
	public int getTaskCompleted() {
		return taskCompleted;
	}
	
	public long getTimeOfProcessing() {
		return timeOfProcessing;
	}
	
	public void setResult(String result){
		this.result = result;
	}
	
	public void setTaskQueued(int taskQueued){
		this.taskQueued = taskQueued;
	}
	
	public void setTaskCompleted(int taskCompleted){
		this.taskCompleted = taskCompleted;
	}
	
	public void setTimeOfProcessing(long timeOfProcessing){
		this.timeOfProcessing = timeOfProcessing;
	}
	
}
