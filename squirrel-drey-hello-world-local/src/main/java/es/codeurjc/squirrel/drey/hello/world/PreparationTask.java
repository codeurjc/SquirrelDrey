package es.codeurjc.squirrel.drey.hello.world;

import java.util.concurrent.atomic.AtomicLong;

import es.codeurjc.squirrel.drey.local.Task;

public class PreparationTask extends Task {

	private Integer numberOfAtomicTasks;

	public PreparationTask(Integer numberOfAtomicTasks) {
		this.numberOfAtomicTasks = numberOfAtomicTasks;
	}

	@Override
	public void process() throws Exception {
		AtomicLong atomicLong = this.getAtomicLong("my_countdown");
		atomicLong.set(this.numberOfAtomicTasks);

		for (int i = 0; i < this.numberOfAtomicTasks; i++) {
			try {
				addNewTask(new AtomicTask());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}