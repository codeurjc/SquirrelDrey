package es.codeurjc.squirrel.drey.loadapp;

import es.codeurjc.squirrel.drey.loadapp.task.MemTask;
import es.codeurjc.squirrel.drey.loadapp.task.PiDigitsTask;

public class LoadTaskParameters {

	private String typeOfTask;
	private Integer taskLoad;
	private Integer maxTaskDuration;
	private Integer delay;

	public Class<?> getTypeOfTask() {
		switch (this.typeOfTask) {
		case "PiDigitsTask":
			return PiDigitsTask.class;
		case "MemTask":
			return MemTask.class;
		default:
			return PiDigitsTask.class;
		}
	}

	public void setTypeOfTask(String typeOfTask) {
		this.typeOfTask = typeOfTask;
	}

	public Integer getTaskLoad() {
		return taskLoad;
	}

	public void setTaskLoad(Integer taskLoad) {
		this.taskLoad = taskLoad;
	}

	public Integer getMaxTaskDuration() {
		return maxTaskDuration;
	}

	public void setMaxTaskDuration(Integer maxTaskDuration) {
		this.maxTaskDuration = maxTaskDuration;
	}

	public Integer getDelay() {
		return delay;
	}

	public void setDelay(Integer delay) {
		this.delay = delay;
	}

}