package io.pablofuente.distributed.algorithm.aws.web;

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

import io.pablofuente.distributed.algorithm.aws.project.Project;

@Controller
public class AlgorithmController {

	@Autowired
	ProjectManager projectManager;

	@RequestMapping(value = "/")
	public String index() {
		return "index";
	}

	@RequestMapping(value = "/solve", method = RequestMethod.POST)
	public String solveProject(@RequestBody List<ProjectFields> projects, Model model) throws InterruptedException {

		List<ProjectFields> projectFields = new ArrayList<>();

		for (ProjectFields project : projects) {
			Project p = projectManager.newProject(project.getId(), project.getData());
			try {
				projectManager.solveProject(p);
			} catch (Exception e) {
				e.printStackTrace();
			}
			ProjectFields pf = new ProjectFields();
			pf.setId(p.getId());
			pf.setData(p.getData());
			projectFields.add(pf);
		}

		model.addAttribute("projects", projectFields);
		return "solve";
	}

	@RequestMapping(value = "/result", method = RequestMethod.GET)
	public ResponseEntity<List<Response>> getResult(@RequestParam(value = "projectIds[]") String[] projectIds) {
		List<Response> l = new ArrayList<>();
		for (String pId : projectIds) {
			Project p = this.projectManager.getProject(pId);
			if (p != null) {
				String result = p.getResult();
				Integer tasksQueued = p.getTasksQueued();
				Integer tasksCompleted = p.getTasksCompleted();
				Long time = p.getTimeOfProcessing();

				l.add(new Response(result == null ? "" : result, tasksQueued == null ? 0 : tasksQueued, tasksCompleted == null ? 0 : tasksCompleted, time));
			}
		}
		return ResponseEntity.ok(l);
	}

}
