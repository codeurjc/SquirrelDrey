package io.pablofuente.distributed.algorithm.aws.project;

import java.util.Map;
import java.util.Map.Entry;

import com.hazelcast.core.ITopic;

public class ProjectSolveTask extends ProjectTask {

	private static final long serialVersionUID = 1L;
	private Integer finalResult;
	
	public ProjectSolveTask(Project project) {
		super(project);
	}

	@Override
	public String call() throws Exception {
		this.publishProjectStats();
		Map<Integer, Integer> results = hazelcastInstance.getMap("results-" + this.project.getId());
		this.finalResult = 0;
		for (Entry<Integer, Integer> e : results.entrySet()) {
			this.finalResult += e.getValue();
		}
		return Integer.toString(finalResult);
	}

	@Override
	public String callback() {
		super.callback();
		ITopic<MyEvent> topic = hazelcastInstance.getTopic("project-solved");
		topic.publish(new MyEvent(this.project.getId(), "project-solved", this.finalResult));
		return Integer.toString(this.finalResult);
	}

}
