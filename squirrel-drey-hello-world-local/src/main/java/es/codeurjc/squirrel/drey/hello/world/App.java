package es.codeurjc.squirrel.drey.hello.world;

import es.codeurjc.squirrel.drey.AlgorithmManager;
import es.codeurjc.squirrel.drey.Task;

/**
 * @author Pablo Fuente (pablo.fuente@urjc.es)
 */
public class App {

	public static void main(String[] args) throws Exception {
		AlgorithmManager<String> manager = new AlgorithmManager<>();
		Task initialTask = new PreparationTask(10);

		manager.solveAlgorithm("sample_algorithm", initialTask, 1, (result) -> {
			System.out.println("MY RESULT: " + result);
			System.exit(0);
		});
	}
}
