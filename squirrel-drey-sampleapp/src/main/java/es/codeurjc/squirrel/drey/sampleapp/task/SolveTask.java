package es.codeurjc.squirrel.drey.sampleapp.task;

import es.codeurjc.squirrel.drey.Task;

public class SolveTask extends Task<String> {

	private static final long serialVersionUID = 1L;

	@Override
	public void process() throws Exception {
		this.algorithmSolved("DONE");
	}

}
