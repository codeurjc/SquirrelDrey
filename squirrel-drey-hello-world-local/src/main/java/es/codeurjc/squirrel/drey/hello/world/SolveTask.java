package es.codeurjc.squirrel.drey.hello.world;

import java.util.Map;
import java.util.Map.Entry;

import es.codeurjc.squirrel.drey.local.Task;

public class SolveTask extends Task {

	@Override
	public void process() throws Exception {
		Map<Integer, Integer> results = (Map<Integer, Integer>) this.getMap("my_results");

		Integer finalResult = 0;
		for (Entry<Integer, Integer> e : results.entrySet()) {
			finalResult += e.getValue();
		}

		this.algorithmSolved(Integer.toString(finalResult));
	}
}