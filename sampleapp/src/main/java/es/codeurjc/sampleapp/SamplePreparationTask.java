package es.codeurjc.sampleapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.IAtomicLong;

import es.codeurjc.distributed.algorithm.Task;

public class SamplePreparationTask extends Task<Void> {

	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(SamplePreparationTask.class);
	
	private String inputData;
	private String atomicLongId;
	private Integer numberOfTasks;
	private Integer taskDuration;
	private Integer timeout;

	public SamplePreparationTask(String inputData, Integer numberOfTasks, Integer taskDuration, Integer timeout, String atomicLongId) {
		this.inputData = inputData;
		this.atomicLongId = atomicLongId;
		this.numberOfTasks = numberOfTasks;
		this.taskDuration = taskDuration;
		this.timeout = timeout;
	}

	@Override
	public void process() throws Exception {
		Thread.sleep(this.timeout * 1000);
		
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong(this.atomicLongId);
		List<SampleAtomicTask> atomicTasks = this.generateAtomicTasks();
		atomicLong.set(this.numberOfTasks);
		for (SampleAtomicTask t : atomicTasks) {
			try {
				addNewTask(t);
				publishQueueStats();
			} catch (Exception e) {
				log.error("Error while processing task [" + t.getWorkDescription() + "]");
				e.printStackTrace();
			}
		}
	}

	private List<SampleAtomicTask> generateAtomicTasks() {
		List<SampleAtomicTask> atomicTasks = new ArrayList<>();
		Random rand = new Random(Math.abs(Long.valueOf(this.inputData.hashCode())));
		for (int i = 0; i < this.numberOfTasks; i++) {
			SampleAtomicTask t = new SampleAtomicTask("results-" + this.algorithmId, this.atomicLongId, Integer.toString(rand.nextInt((50000) + 1)), this.taskDuration, Integer.toString(i));
			atomicTasks.add(t);
		}
		return atomicTasks;
	}

}
