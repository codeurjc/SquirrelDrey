package es.codeurjc.squirrel.drey.hello.world;

import es.codeurjc.squirrel.drey.AlgorithmManager;
import es.codeurjc.squirrel.drey.Task;
import es.codeurjc.squirrel.drey.Worker;

/**
 * @author Pablo Fuente (pablo.fuente@urjc.es)
 */
public class App {

	public static void main(String[] args) throws Exception {

		boolean isWorker = System.getProperty("worker") != null ? Boolean.valueOf(System.getProperty("worker")) : true;

		if (!isWorker) {

			// Solve the algorithm

			AlgorithmManager<String> manager = new AlgorithmManager<>();
			Task initialTask = new PreparationTask(10);

			manager.solveAlgorithm("sample_algorithm", initialTask, 1, (result) -> {
				System.out.println("MY RESULT: " + result);
				System.exit(0);
			});

		} else {

			// Launch a worker

			Worker.launch();

		}
	}
}
