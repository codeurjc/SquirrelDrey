package es.codeurjc.squirrel.drey.hello.world;

import java.util.Map.Entry;

import com.hazelcast.core.IMap;

import es.codeurjc.squirrel.drey.Task;

public class SolveTask extends Task {

	@Override
	public void process() throws Exception {
		IMap<Integer, Integer> results = (IMap<Integer, Integer>) this.getMap("my_results");
		
		Integer finalResult = 0;
		for (Entry<Integer, Integer> e : results.entrySet()) {
			finalResult += e.getValue();
		}
		
		this.algorithmSolved(Integer.toString(finalResult));
	}
}