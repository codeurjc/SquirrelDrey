package es.codeurjc.squirrel.drey.loadapp.task;

import java.util.ArrayList;
import java.util.List;

import es.codeurjc.squirrel.drey.Task;
import es.codeurjc.squirrel.drey.loadapp.LoadTaskParameters;

public class InsertionTask extends Task {

	private static final long serialVersionUID = 1L;

	private String customId;
	private InsertionTask nextInsertionTask;
	private List<Task> tasks;

	public InsertionTask(String customId, InsertionTask nextInsertionTask, List<LoadTaskParameters> loadParameters) {

		this.customId = customId;
		this.nextInsertionTask = nextInsertionTask;
		this.tasks = new ArrayList<>();

		if (nextInsertionTask != null) {
			LoadTaskParameters params;
			for (int i = 0; i < loadParameters.size(); i++) {
				params = loadParameters.get(i);
				if (params.getTypeOfTask() == PiDigitsTask.class) {
					this.tasks.add(new PiDigitsTask(params.getTaskLoad(), params.getDelay(),
							params.getMaxTaskDuration(), this.getCustomId(), nextInsertionTask));
				} else if (params.getTypeOfTask() == MemTask.class) {
					// this.tasks.add(new MemTask(params.getTaskLoad(), params.getDelay(),
					// params.getMaxTaskDuration()));
				}
			}
		}
	}

	@Override
	public void process() throws Exception {
		this.getAtomicLong("countdown" + this.getCustomId()).set(tasks.size());
		if (tasks.isEmpty()) {
			this.algorithmSolved("SOLVED");
		} else {
			tasks.forEach(t -> addNewTask(t));
		}
	}

	public String getCustomId() {
		return customId;
	}

	public InsertionTask getNextInsertionTask() {
		return nextInsertionTask;
	}

}
