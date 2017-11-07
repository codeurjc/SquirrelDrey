package es.codeurjc.squirrel.drey.hello.world;

import java.util.Map;
import java.util.Map.Entry;

import es.codeurjc.squirrel.drey.Task;

public class SolveTask extends Task<String> {

	@Override
	public void process() throws Exception {
		Map<Integer, Integer> results = hazelcastInstance.getMap("my_results");
		
		Integer finalResult = 0;
		for (Entry<Integer, Integer> e : results.entrySet()) {
			finalResult += e.getValue();
		}
		
		this.setResult(Integer.toString(finalResult));
		this.algorithmSolved(Integer.toString(finalResult));
	}
}