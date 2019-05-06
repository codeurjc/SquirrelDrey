package es.codeurjc.squirrel.drey.loadapp.task;

import es.codeurjc.squirrel.drey.Task;

public class MemTask extends LoadTask {

	private static final long serialVersionUID = 1L;

	private int megabytes;

	public MemTask(int megabytes, int delay, int taskDuration, String parentTaskId, Task nextInsertionTask) {
		super(delay, taskDuration, parentTaskId, nextInsertionTask);
		this.megabytes = megabytes;
	}

	@Override
	public void process() throws Exception {
		super.runDelay();

		// TODO: add memory load according to 'megabytes' param

		super.addNewInsertionTaskIfNecessary();
	}

}
