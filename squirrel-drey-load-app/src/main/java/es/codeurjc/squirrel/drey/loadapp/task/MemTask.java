package es.codeurjc.squirrel.drey.loadapp.task;

import java.util.LinkedHashMap;
import java.util.Map;

import es.codeurjc.squirrel.drey.Task;

public class MemTask extends LoadTask {

	class DummyObject {
		Integer dummyInt = 1234;
		Double dummyDouble = 1234.1;
	}

	private static final long serialVersionUID = 1L;

	private int megabytes;

	public MemTask(int megabytes, int delay, int taskDuration, String parentTaskId, Task nextInsertionTask) {
		super(delay, taskDuration, parentTaskId, nextInsertionTask);
		this.megabytes = megabytes;
	}

	@Override
	public void process() throws Exception {

		super.runDelay();

		Map<String, Object> dummyMap = new LinkedHashMap<>();
		for (int i = 0; i < megabytes; i++) {
			dummyMap.put(Integer.toString(i), new DummyObject());
		}

		dummyMap.clear();

		super.addNewInsertionTaskIfNecessary();
	}

}
