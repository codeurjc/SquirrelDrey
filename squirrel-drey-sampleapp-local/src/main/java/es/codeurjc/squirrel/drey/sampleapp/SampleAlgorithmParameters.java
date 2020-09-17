package es.codeurjc.squirrel.drey.sampleapp;

public class SampleAlgorithmParameters {

	private String id;
	private String inputData;
	private Integer priority;
	private Integer numberOfTasks;
	private Integer taskDuration;
	private Integer timeout;
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	public String getInputData() {
		return inputData;
	}

	public void setInputData(String inputData) {
		this.inputData = inputData;
	}
	
	public Integer getPriority() {
		return priority;
	}
	
	public void setPriority(Integer priority) {
		this.priority = priority;
	}
	
	public Integer getNumberOfTasks() {
		return numberOfTasks;
	}

	public void setNumberOfTasks(Integer numberOfTasks) {
		this.numberOfTasks = numberOfTasks;
	}
	
	public Integer getTaskDuration() {
		return taskDuration;
	}

	public void setTaskDuration(Integer taskDuration) {
		this.taskDuration = taskDuration;
	}
	
	public Integer getTimeout() {
		return timeout;
	}

	public void setTimeout(Integer timeout) {
		this.timeout = timeout;
	}
	

}
