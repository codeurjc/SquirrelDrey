package es.codeurjc.squirrel.drey.loadapp;

import es.codeurjc.squirrel.drey.loadapp.task.MemTask;
import es.codeurjc.squirrel.drey.loadapp.task.PiDigitsTask;

public class LoadTaskParameters {

	private String type;
	private Integer load;
	private Integer duration;
	private Integer delay;

	public Class<?> getType() {
		switch (this.type) {
		case "PiDigitsTask":
			return PiDigitsTask.class;
		case "MemTask":
			return MemTask.class;
		default:
			return PiDigitsTask.class;
		}
	}

	public void setType(String type) {
		this.type = type;
	}

	public Integer getLoad() {
		return load;
	}

	public void setLoad(Integer load) {
		this.load = load;
	}

	public Integer getDuration() {
		return duration;
	}

	public void setDuration(Integer duration) {
		this.duration = duration;
	}

	public Integer getDelay() {
		return delay;
	}

	public void setDelay(Integer delay) {
		this.delay = delay;
	}

}