package es.codeurjc.squirrel.drey.loadapp.task;

import com.hazelcast.core.IAtomicLong;

import es.codeurjc.squirrel.drey.Task;
import es.codeurjc.squirrel.drey.loadapp.App;

public class LoadTask extends Task {

	private static final long serialVersionUID = 1L;

	private int delay;
	private String parentTaskId;
	private Task nextInsertionTask;

	public LoadTask(int delay, int maxTaskDuration, String parentTaskId, Task nextInsertionTask) {
		this.delay = delay;
		this.parentTaskId = parentTaskId;
		this.nextInsertionTask = nextInsertionTask;
		this.setMaxDuration(maxTaskDuration * 1000);
	}

	public void runDelay() {
		try {
			Thread.sleep(this.delay * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void addNewInsertionTaskIfNecessary() {
		IAtomicLong atomicLong = this.getAtomicLong("countdown" + parentTaskId);
		if (atomicLong.decrementAndGet() == 0L) {
			App.logger.info("Last load task executed for insertion task [{}]", this);
			addNewTask(nextInsertionTask);
			atomicLong.destroy();
		}
	}

}
