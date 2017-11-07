package es.codeurjc.squirrel.drey.hello.world;

import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IMap;

import es.codeurjc.squirrel.drey.Task;

public class AtomicTask extends Task<Integer> {

	public AtomicTask() {	}

	@Override
	public void process() throws Exception {
		Thread.sleep(5000);
		
		this.setResult(1);
		
		IMap<Integer, Integer> results = hazelcastInstance.getMap("my_results");
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong("my_countdown");
		results.put(this.getId(), this.getResult());
		
		if (atomicLong.decrementAndGet() == 0L) {
			System.out.println("ADDING SOLVE TASK FOR ALGORITHM " + this.algorithmId);
			addNewTask(new SolveTask());
		}
	}
}