package es.codeurjc.squirrel.drey.sampleapp;

import java.util.Map;
import java.util.Map.Entry;

import es.codeurjc.squirrel.drey.Task;

public class SampleSolveTask extends Task<String> {

	private static final long serialVersionUID = 1L;

	@Override
	public void process() throws Exception {
		Map<Integer, Integer> results = hazelcastInstance.getMap("results-" + this.algorithmId);
		
		Integer finalResult = 0;
		for (Entry<Integer, Integer> e : results.entrySet()) {
			finalResult += e.getValue();
		}
		
		this.setResult(Integer.toString(finalResult));
		this.algorithmSolved(Integer.toString(finalResult));
	}

}
