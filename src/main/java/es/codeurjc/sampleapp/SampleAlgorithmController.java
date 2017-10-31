package es.codeurjc.sampleapp;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import es.codeurjc.distributed.algorithm.Algorithm;
import es.codeurjc.distributed.algorithm.AlgorithmManager;
import es.codeurjc.distributed.algorithm.Task;
import es.codeurjc.distributed.algorithm.WorkerStats;

@Controller
public class SampleAlgorithmController {

	@Autowired
	AlgorithmManager<String> algorithmManager;

	@RequestMapping(value = "/")
	public String index() {
		return "index";
	}

	@RequestMapping(value = "/solve", method = RequestMethod.POST)
	public String solveAlgorithm(@RequestBody List<SampleAlgorithmParameters> algorithms, Model model) throws InterruptedException {
		List<SampleAlgorithmParameters> algorithmFields = new ArrayList<>();
		for (SampleAlgorithmParameters algorithm : algorithms) {
			try {
				Task<Void> initialTask = new SamplePreparationTask(algorithm.getInputData(), algorithm.getNumberOfTasks(), 
						algorithm.getTaskDuration(), algorithm.getTimeout(), "countdown-" + algorithm.getId());
				algorithmManager.solveAlgorithm(algorithm.getId(), initialTask, algorithm.getPriority(), (result) -> {
					System.out.println("RESULT: " + result.toString());
				});
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

}
