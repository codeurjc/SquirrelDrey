package es.codeurjc.squirrel.drey.sampleapp;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

	@RequestMapping(value = "/")
	public String index() {
		algorithmManager.clearAlgorithms();
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
					Task<Void> initialTask = new PreparationTask(algorithm.getInputData(), algorithm.getNumberOfTasks(), 
							algorithm.getTaskDuration(), "countdown-" + algorithm.getId());
					try {
						algorithmManager.solveAlgorithm(algorithm.getId(), initialTask, algorithm.getPriority(), (result) -> {
							log.info("RESULT FOR ALGORITHM {}: {}", algorithm.getId(), result.toString());
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
			Algorithm<String> alg = this.algorithmManager.getAlgorithm(id);
			if (alg != null) {
				String result = alg.getResult();
				Integer tasksQueued = alg.getTasksQueued();
				Integer tasksCompleted = alg.getTasksCompleted();
				Long time = alg.getTimeOfProcessing();

				l1.add(new AlgorithmStats(result == null ? "" : result, tasksQueued == null ? 0 : tasksQueued, tasksCompleted == null ? 0 : tasksCompleted, time));
			}
		}
		
		// Workers statistics
		List<WorkerStats> l2 = new ArrayList<>();
		for (String id : this.algorithmManager.getWorkers().keySet()) {
			l2.add(this.algorithmManager.getWorkers().get(id));
		}
		
		Response response = new Response(l1, l2);
		return ResponseEntity.ok(response);
	}
	
	@RequestMapping(value = "/stop", method = RequestMethod.POST)
	public ResponseEntity<String> stopAlgorithms() {
		log.info("TERMINATING ALL ALGORITHMS...");
		try {
			this.algorithmManager.blockingTerminateAlgorithms();
		} catch (InterruptedException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		log.info("...ALL ALGORITHMS TERMINATED");
		return ResponseEntity.status(HttpStatus.OK).build();
	}
	
	@RequestMapping(value = "/stop-one", method = RequestMethod.POST)
	public ResponseEntity<String> stopOneAlgorithm(@RequestParam(value = "algorithmId", required = true) String algorithmId) {
		log.info("TERMINATING ALGORITHM {}...", algorithmId);
		try {
			this.algorithmManager.blockingTerminateOneAlgorithm(algorithmId);
		} catch (InterruptedException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		log.info("...ALGORITHM {} TERMINATED", algorithmId);
		return ResponseEntity.status(HttpStatus.OK).build();
	}
}
