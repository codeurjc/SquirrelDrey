package io.pablofuente.distributed.algorithm.aws.project;

public class Response {

	int taskQueued;
	String result;
	long timeOfProcessing;

	public Response(String result, int taskQueued, long timeOfProcessing) {
		this.result = result;
		this.taskQueued = taskQueued;
		this.timeOfProcessing = timeOfProcessing;
	}
	
	public String getResult() {
		return result;
	}
	
	public int getTaskQueued() {
		return taskQueued;
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
	
	public void setTimeOfProcessing(long timeOfProcessing){
		this.timeOfProcessing = timeOfProcessing;
	}

}
