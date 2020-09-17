package es.codeurjc.squirrel.drey.sampleapp.task;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import es.codeurjc.squirrel.drey.Task;
import es.codeurjc.squirrel.drey.sampleapp.App;

public class AtomicTask extends Task {

	private static final long serialVersionUID = 1L;
	private String workData;
	private Integer taskDuration;
	private String workDescription;

	public AtomicTask(String workData, Integer taskDuration, String workDescription) {
		this.workData = workData;
		this.taskDuration = taskDuration;
		this.workDescription = workDescription;
	}

	public String getWorkData() {
		return this.workData;
	}

	public String getWorkDescription() {
		return this.workDescription;
	}

	@Override
	public void process() throws Exception {
		Thread.sleep(taskDuration * 1000);
		
		Map<Integer, Integer> results = (Map<Integer, Integer>) this.getMap("results");
		AtomicLong atomicLong = this.getAtomicLong("countdown");
		results.put(Integer.parseInt(this.workDescription), 1);
				
		if (atomicLong.decrementAndGet() == 0L) {
			App.logger.info("ADDING SOLVE TASK FOR ALGORITHM [{}]", this.algorithmId);
			addNewTask(new ResultTask());
		}
	}
	
}
