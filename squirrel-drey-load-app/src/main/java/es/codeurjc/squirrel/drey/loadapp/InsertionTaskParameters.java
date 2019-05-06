package es.codeurjc.squirrel.drey.loadapp;

import java.util.List;

import es.codeurjc.squirrel.drey.loadapp.task.MemTask;
import es.codeurjc.squirrel.drey.loadapp.task.PiDigitsTask;

public class InsertionTaskParameters {

	private String id;
	private List<LoadTaskParameters> loadTasks;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<LoadTaskParameters> getLoadTasks() {
		return loadTasks;
	}

	public void setLoadTasks(List<LoadTaskParameters> loadTasks) {
		this.loadTasks = loadTasks;
	}

}
