package es.codeurjc.squirrel.drey.sampleapp.task;

import es.codeurjc.squirrel.drey.local.Task;

public class SolveTask extends Task {

	private static final long serialVersionUID = 1L;

	@Override
	public void process() throws Exception {
		
		// No need of doing this to end the algorithm execution: SquirrelDrey knows
		// when an algorithm has ended by checking its added, completed and queued tasks.
		// Result would simply be null. If the algorithm needs to return some result,
		// calling this method on the last Task with the desire value is mandatory
		
		this.algorithmSolved("DONE");
	}

}
