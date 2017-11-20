package es.codeurjc.squirrel.drey.sampleapp.task;

import java.util.Map;
import java.util.Map.Entry;

import es.codeurjc.squirrel.drey.Task;

public class ResultTask extends Task {

	private static final long serialVersionUID = 1L;
	
	private final int NUMBER_OF_FILES = 3;

	@Override
	public void process() throws Exception {
		Map<Integer, Integer> results = (Map<Integer, Integer>) this.getMap("results");
		
		Integer finalResult = 0;
		for (Entry<Integer, Integer> e : results.entrySet()) {
			finalResult += e.getValue();
		}
		
		this.getAtomicLong("file_tasks").set(NUMBER_OF_FILES);
		
		for (int i = 0; i < NUMBER_OF_FILES; i++) {
			addNewTask(new FileTask(i+1, Integer.toString(finalResult)));
		}
	}

}
