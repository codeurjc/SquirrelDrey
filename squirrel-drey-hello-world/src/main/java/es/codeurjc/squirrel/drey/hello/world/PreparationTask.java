package es.codeurjc.squirrel.drey.hello.world;

import java.util.ArrayList;
import java.util.List;

import com.hazelcast.core.IAtomicLong;

import es.codeurjc.squirrel.drey.Task;

public class PreparationTask extends Task<Void> {
	
	private Integer numberOfAtomicTasks;

	public PreparationTask(Integer numberOfAtomicTasks) {
		this.numberOfAtomicTasks = numberOfAtomicTasks;
	}

	@Override
	public void process() throws Exception {
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong("my_countdown");
		atomicLong.set(this.numberOfAtomicTasks);
		
		List<AtomicTask> atomicTasks = new ArrayList<>();
		for (int i = 0; i < this.numberOfAtomicTasks; i++) {
			atomicTasks.add(new AtomicTask());
		}
		
		for (AtomicTask t : atomicTasks) {
			try {
				addNewTask(t);
				publishQueueStats();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}