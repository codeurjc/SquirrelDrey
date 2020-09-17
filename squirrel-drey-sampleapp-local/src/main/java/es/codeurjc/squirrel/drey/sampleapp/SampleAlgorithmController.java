package es.codeurjc.squirrel.drey.sampleapp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import org.springframework.web.bind.annotation.RequestParam;

import es.codeurjc.squirrel.drey.Algorithm;
import es.codeurjc.squirrel.drey.AlgorithmManager;
import es.codeurjc.squirrel.drey.Task;
import es.codeurjc.squirrel.drey.WorkerStats;
import es.codeurjc.squirrel.drey.sampleapp.task.PreparationTask;

@Controller
public class SampleAlgorithmController {
	
	private static final Logger log = LoggerFactory.getLogger(SampleAlgorithmController.class);

	@Autowired
	AlgorithmManager<String> algorithmManager;
	
	Map<String, Algorithm<String>> solvedAlgorithms = new ConcurrentHashMap<>();

	@RequestMapping(value = "/")
	public String index() {
		return "index";
	}

	@RequestMapping(value = "/solve", method = RequestMethod.POST)
	public String solveAlgorithm(@RequestBody List<SampleAlgorithmParameters> algorithms, Model model) throws InterruptedException {
		List<SampleAlgorithmParameters> algorithmFields = new ArrayList<>();
		for (SampleAlgorithmParameters algorithm : algorithms) {
			try {
				new Thread(() -> {
					try {
						Thread.sleep(1000 * algorithm.getTimeout());
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					Task initialTask = new PreparationTask(algorithm.getInputData(), algorithm.getNumberOfTasks(), algorithm.getTaskDuration());
					try {
						algorithmManager.solveAlgorithm(algorithm.getId(), initialTask, algorithm.getPriority(), (result) -> {
							log.info("RESULT FOR ALGORITHM {}: {}", algorithm.getId(), result == null ? "NULL" : result.toString());
							Algorithm<String> resultAlg = algorithmManager.getAlgorithm(algorithm.getId());
							log.info("TASKS ADDED: {}", resultAlg.getTasksAdded());
							log.info("TASKS COMPLETED: {}", resultAlg.getTasksCompleted());
							log.info("TASKS IN THE QUEUE: {}", resultAlg.getTasksQueued());
							log.info("TIME OF PROCESSING: {}", resultAlg.getTimeOfProcessing());
							// Store the algorithm to send one final response to front
							solvedAlgorithms.put(algorithm.getId(), resultAlg);
						});
					} catch (Exception e) {
						e.printStackTrace();
					}
				}).start();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			SampleAlgorithmParameters algParams = new SampleAlgorithmParameters();
			
			algParams.setId(algorithm.getId());
			algParams.setInputData(algorithm.getInputData());
			algParams.setPriority(algorithm.getPriority());
			algParams.setNumberOfTasks(algorithm.getNumberOfTasks());
			algParams.setTaskDuration(algorithm.getTaskDuration());
			algParams.setTimeout(algorithm.getTimeout());
			
			algorithmFields.add(algParams);
		}

		model.addAttribute("algorithms", algorithmFields);
		return "solve";
	}

	@RequestMapping(value = "/statistics", method = RequestMethod.GET)
	public ResponseEntity<Response> getResult(@RequestParam(value = "algorithmIds[]") String[] algorithmIds) {
		
		// Algorithms statistics
		List<AlgorithmStats> l1 = new ArrayList<>();
		for (String id : algorithmIds) {
			
			// Running algorithm. Retrieve from SquirrelDrey's map of running algorithms
			Algorithm<String> alg = this.algorithmManager.getAlgorithm(id);
			if (alg != null) {
				String result = alg.getResult();
				//TODO: Add correct values
				Integer tasksAdded = 0;
				Integer tasksCompleted = 0;
				Integer tasksQueued = 0;
				Long time = alg.getTimeOfProcessing();
				
				l1.add(new AlgorithmStats(alg.getId(), (result == null) ? "" : result, tasksAdded, tasksCompleted, tasksQueued, time));
				
			} else {
				
				// Solved algorithm. Retrieve from custom map of completed algorithms
				alg = this.solvedAlgorithms.get(id);
				if (alg != null) {
					String result = alg.getResult();
					Integer tasksAdded = alg.getTasksAdded();
					Integer tasksCompleted = alg.getTasksCompleted();
					Integer tasksQueued = alg.getTasksQueued();
					Long time = alg.getTimeOfProcessing();
					
					l1.add(new AlgorithmStats(alg.getId(), (result == null) ? "" : result, tasksAdded, tasksCompleted, tasksQueued, time));
					
					// Don't need the stats of the solved algorithm anymore
					this.solvedAlgorithms.remove(id);
				}
				
			}
		}
		
		// Workers statistics
		List<WorkerStats> l2 = new ArrayList<>();
		try {
			Map<String, WorkerStats> fetchedWorkers = this.algorithmManager.getWorkers();
			for (String id : fetchedWorkers.keySet()) {
				l2.add(fetchedWorkers.get(id));
			}
		} catch (Exception e) {
			log.error(e.getMessage());
			e.printStackTrace();
		}
		Response response = new Response(l1, l2);
		return ResponseEntity.ok(response);
	}
	
	@RequestMapping(value = "/stop", method = RequestMethod.POST)
	public ResponseEntity<String> stopAlgorithms() throws IOException {
		log.info("TERMINATING ALL ALGORITHMS...");
		try {
			this.algorithmManager.blockingTerminateAlgorithms();
		} catch (InterruptedException e) {
			return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
		}
		log.info("...ALL ALGORITHMS TERMINATED");
		return ResponseEntity.status(HttpStatus.SC_OK).build();
	}
	
	@RequestMapping(value = "/stop-one", method = RequestMethod.POST)
	public ResponseEntity<String> stopOneAlgorithm(@RequestParam(value = "algorithmId", required = true) String algorithmId) throws IOException {
		log.info("TERMINATING ALGORITHM {}...", algorithmId);
		try {
			this.algorithmManager.blockingTerminateOneAlgorithm(algorithmId);
		} catch (InterruptedException e) {
			return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
		}
		log.info("...ALGORITHM {} TERMINATED", algorithmId);
		return ResponseEntity.status(HttpStatus.SC_OK).build();
	}
}
