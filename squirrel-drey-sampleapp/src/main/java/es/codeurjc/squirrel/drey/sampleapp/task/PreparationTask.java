package es.codeurjc.squirrel.drey.sampleapp.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.IAtomicLong;

import es.codeurjc.squirrel.drey.Task;

public class PreparationTask extends Task<Void> {

	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(PreparationTask.class);
	
	private String inputData;
	private String atomicLongId;
	private Integer numberOfTasks;
	private Integer taskDuration;

	public PreparationTask(String inputData, Integer numberOfTasks, Integer taskDuration, String atomicLongId) {
		this.inputData = inputData;
		this.atomicLongId = atomicLongId;
		this.numberOfTasks = numberOfTasks;
		this.taskDuration = taskDuration;
	}

	@Override
	public void process() throws Exception {
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong(this.atomicLongId);
		List<AtomicTask> atomicTasks = this.generateAtomicTasks();
		atomicLong.set(this.numberOfTasks);
		for (AtomicTask t : atomicTasks) {
			try {
				addNewTask(t);
				publishQueueStats();
			} catch (Exception e) {
				log.error("Error while processing task [" + t.getWorkDescription() + "]");
				e.printStackTrace();
			}
		}
	}

	private List<AtomicTask> generateAtomicTasks() {
		List<AtomicTask> atomicTasks = new ArrayList<>();
		Random rand = new Random(Math.abs(Long.valueOf(this.inputData.hashCode())));
		for (int i = 0; i < this.numberOfTasks; i++) {
			AtomicTask t = new AtomicTask("results-" + this.algorithmId, this.atomicLongId, Integer.toString(rand.nextInt((50000) + 1)), this.taskDuration, Integer.toString(i));
			atomicTasks.add(t);
		}
		return atomicTasks;
	}

}
