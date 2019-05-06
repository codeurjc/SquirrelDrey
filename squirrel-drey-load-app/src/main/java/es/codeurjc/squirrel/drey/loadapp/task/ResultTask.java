package es.codeurjc.squirrel.drey.loadapp.task;

import java.math.BigDecimal;
import java.util.Collection;

import es.codeurjc.squirrel.drey.Task;

public class ResultTask extends Task {

	private static final long serialVersionUID = 1L;

	@Override
	public void process() throws Exception {
		Collection<BigDecimal> results = (Collection<BigDecimal>) this.getList("results");
		System.out.println("Number of results: " + results.size());
		for (BigDecimal pi : results) {
			System.out.println("Result: " + pi.toString());
		}
		this.algorithmSolved("DONE");
	}

}
