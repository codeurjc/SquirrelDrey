package es.codeurjc.squirrel.drey.loadapp;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import es.codeurjc.squirrel.drey.Algorithm;
import es.codeurjc.squirrel.drey.Algorithm.Status;
import es.codeurjc.squirrel.drey.AlgorithmCallback;
import es.codeurjc.squirrel.drey.AlgorithmManager;
import es.codeurjc.squirrel.drey.WorkerStats;
import es.codeurjc.squirrel.drey.loadapp.task.InsertionTask;

@Controller
public class SampleAlgorithmController {

	private static final Logger log = LoggerFactory.getLogger(SampleAlgorithmController.class);

	@Autowired
	AlgorithmManager<String> algorithmManager;

	Algorithm<String> algorithm;
	Algorithm<String> terminatedAlgorithm;

	final String DEFAULT_ALGORITHM_ID = "LOAD_ALGORITHM";
	String INUSE_ALGORITHM_ID = DEFAULT_ALGORITHM_ID;

	@RequestMapping(value = "/")
	public String index() {
		return "index";
	}

	@RequestMapping(value = "/solve", method = RequestMethod.POST)
	public String solveAlgorithm(@RequestBody List<InsertionTaskParameters> tasks, Model model) throws Exception {

		InsertionTask initialTask = new InsertionTask(tasks.get(0).getId(), recursiveTaskGenerator(tasks, 1),
				tasks.get(0).getLoadTasks());

		int numberOfTasks = tasks.stream().map(insertionTaskParams -> insertionTaskParams.getLoadTasks().size())
				.reduce(0, Integer::sum);
		numberOfTasks += tasks.size();

		String algId = algorithmManager.solveAlgorithm(DEFAULT_ALGORITHM_ID, initialTask, 1,
				new AlgorithmCallback<String>() {
					@Override
					public void onSuccess(String result, Algorithm<String> alg) {
						log.info("RESULT FOR ALGORITHM {}: {}", alg.getId(),
								result == null ? "NULL" : result.toString());
						log.info("STATUS FOR ALGORITHM {}: {}", alg.getId(), alg.getStatus());
						// Store the algorithm to send one final response to front
						algorithm = alg;
					}

					@Override
					public void onError(Algorithm<String> alg) {
						log.error("ERROR WHILE SOLVING ALGORITHM {}. Status: {}", alg.getId(), alg.getStatus());
						alg.getErrorTasks().forEach(errorTask -> {
							log.error("TASK {} TIMEOUT IN ALGORITHM {}", errorTask, alg.getId());
						});
						algorithm = alg;
					}
				});

		if (algId != null) {
			INUSE_ALGORITHM_ID = algId;
		}

		model.addAttribute("numberOfTasks", numberOfTasks);
		return "solve";
	}

	@RequestMapping(value = "/statistics", method = RequestMethod.GET)
	public ResponseEntity<Response> getStats() {

		// Algorithm statistics
		Algorithm<String> alg = this.algorithmManager.getAlgorithm(INUSE_ALGORITHM_ID);
		if (alg == null) {
			alg = this.algorithm;
			if (alg == null) {
				if (this.terminatedAlgorithm == null) {
					return ResponseEntity.ok(null);
				} else {
					alg = this.terminatedAlgorithm;
				}
			}
		}

		AlgorithmStats algorithmStats;
		String result = alg.getResult();
		Status status = alg.getStatus();
		Integer tasksAdded;

		try {
			tasksAdded = alg.getTasksAdded();
		} catch (Exception e) {
			log.warn("The queue for added tasks of the algorithm {} is destroyed. Algorithm terminated", alg.getId());
			algorithmStats = new AlgorithmStats(alg.getId(), (result == null) ? "" : result, status, 0, 0, 0, 0);
			this.algorithm = null;
			this.terminatedAlgorithm = null;
			return ResponseEntity.ok(new Response(algorithmStats, new ArrayList<>()));
		}

		Integer tasksCompleted = alg.getTasksCompleted();
		Integer tasksQueued = alg.getTasksQueued();
		Long time = alg.getTimeOfProcessing();
		algorithmStats = new AlgorithmStats(alg.getId(), (result == null) ? "" : result, status, tasksAdded,
				tasksCompleted, tasksQueued, time);

		// Workers statistics
		List<WorkerStats> workerStats = new ArrayList<>();
		for (String id : this.algorithmManager.getWorkers().keySet()) {
			workerStats.add(this.algorithmManager.getWorkers().get(id));
		}

		Response response = new Response(algorithmStats, workerStats);

		// Delete solved algorithm
		this.algorithm = null;
		this.terminatedAlgorithm = null;

		return ResponseEntity.ok(response);
	}

	@RequestMapping(value = "/stop", method = RequestMethod.POST)
	public ResponseEntity<String> stopOneAlgorithm() {
		log.info("TERMINATING ALGORITHM...");
		try {
			terminatedAlgorithm = this.algorithmManager.blockingTerminateOneAlgorithm(INUSE_ALGORITHM_ID);
			log.info("ALGORITHM {} TERMINATED. Algorithm status: {}", INUSE_ALGORITHM_ID,
					terminatedAlgorithm.getStatus());
		} catch (InterruptedException e) {
			return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
		}
		return ResponseEntity.status(HttpStatus.SC_OK).build();
	}

	private InsertionTask recursiveTaskGenerator(List<InsertionTaskParameters> parameters, int index) {
		if (parameters.size() == 1) {
			return null;
		}
		if (parameters.get(index).getLoadTasks() == null || parameters.get(index).getLoadTasks().isEmpty()) {
			// Last task
			return new InsertionTask(parameters.get(index).getId(), null, null);
		} else {
			return new InsertionTask(parameters.get(index).getId(), recursiveTaskGenerator(parameters, index + 1),
					parameters.get(index).getLoadTasks());
		}
	}

}
