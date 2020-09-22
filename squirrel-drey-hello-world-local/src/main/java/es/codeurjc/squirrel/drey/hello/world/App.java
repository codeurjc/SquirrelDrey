package es.codeurjc.squirrel.drey.hello.world;

import es.codeurjc.squirrel.drey.local.Algorithm;
import es.codeurjc.squirrel.drey.local.AlgorithmManager;
import es.codeurjc.squirrel.drey.local.Task;
import es.codeurjc.squirrel.drey.local.Worker;

/**
 * @author Iván Chicano (ivchicano.urjc@gmail.com)
 */
public class App {

	public static void main(String[] args) throws Exception {
		if (!Boolean.valueOf(System.getProperty("worker"))) {
			AlgorithmManager<String> manager = new AlgorithmManager<>();
			Task initialTask = new PreparationTask(10);

			manager.solveAlgorithm("sample_algorithm", initialTask, 1, (result) -> {
				System.out.println("MY RESULT: " + result);
				System.exit(0);
			});
		} else {
			Worker.launch();
		}
	}
}
