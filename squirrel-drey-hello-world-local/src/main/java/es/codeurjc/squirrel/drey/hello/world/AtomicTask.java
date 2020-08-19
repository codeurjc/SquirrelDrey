package es.codeurjc.squirrel.drey.hello.world;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import es.codeurjc.squirrel.drey.Task;

public class AtomicTask extends Task {

	public AtomicTask() {
	}

	@Override
	public void process() throws Exception {
		Thread.sleep(5000);

		Map<Integer, Integer> results = Structures.resultsMap;
		AtomicLong atomicLong = Structures.countDown;
		results.put(this.getId(), 1);
		if (atomicLong.decrementAndGet() == 0L) {
			System.out.println("ADDING SOLVE TASK FOR ALGORITHM " + this.algorithmId);
			addNewTask(new SolveTask());
		}
	}
}