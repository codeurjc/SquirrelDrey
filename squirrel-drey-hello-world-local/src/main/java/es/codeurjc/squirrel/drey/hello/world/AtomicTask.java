package es.codeurjc.squirrel.drey.hello.world;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import es.codeurjc.squirrel.drey.local.Task;

public class AtomicTask extends Task {

	public AtomicTask() {
	}

	@Override
	public void process() throws Exception {
		Thread.sleep(5000);

		Map<Integer, Integer> results = (Map<Integer, Integer>) this.getMap("my_results");
		AtomicLong atomicLong = this.getAtomicLong("my_countdown");
		results.put(this.getId(), 1);
		if (atomicLong.decrementAndGet() == 0L) {
			System.out.println("ADDING SOLVE TASK FOR ALGORITHM " + this.algorithmId);
			addNewTask(new SolveTask());
		}
	}
}