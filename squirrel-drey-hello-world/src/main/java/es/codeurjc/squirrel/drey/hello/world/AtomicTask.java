package es.codeurjc.squirrel.drey.hello.world;

import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IMap;

import es.codeurjc.squirrel.drey.Task;

public class AtomicTask extends Task {

	public AtomicTask() {	}

	@Override
	public void process() throws Exception {
		Thread.sleep(5000);
		
		IMap<Integer, Integer> results = (IMap<Integer, Integer>) this.getMap("my_results");
		IAtomicLong atomicLong = this.getAtomicLong("my_countdown");
		results.put(this.getId(), 1);
		
		if (atomicLong.decrementAndGet() == 0L) {
			System.out.println("ADDING SOLVE TASK FOR ALGORITHM " + this.algorithmId);
			addNewTask(new SolveTask());
		}
	}
}