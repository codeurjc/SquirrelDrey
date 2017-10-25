package io.pablofuente.distributed.algorithm.aws.app;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;

import io.pablofuente.distributed.algorithm.aws.project.MyEvent;
import io.pablofuente.distributed.algorithm.aws.project.Project;
import io.pablofuente.distributed.algorithm.aws.project.ProjectTask;

public class ProjectManager {

	private HazelcastInstance hzClient;
	private Map<String, Project> projects;

	public ProjectManager() {
		ClientConfig config = new ClientConfig();
		GroupConfig groupConfig = config.getGroupConfig();
		groupConfig.setName("dev");
		groupConfig.setPassword("dev-pass");
		this.hzClient = HazelcastClient.newHazelcastClient(config);

		this.projects = new ConcurrentHashMap<>();

		hzClient.getTopic("project-solved").addMessageListener((message) -> {
			MyEvent ev = (MyEvent) message.getMessageObject();
			System.out.println("PROJECT SOLVED: Project: " + ev.getProjectId() + ", Result: " + ev.getContent());
			Project p = this.projects.get(ev.getProjectId());
			p.setFinishTime(System.currentTimeMillis());
			p.setResult(Integer.toString((int) ev.getContent()));
		});
		hzClient.getTopic("queue-stats").addMessageListener((message) -> {
			MyEvent ev = (MyEvent) message.getMessageObject();
			System.out.println("EXECUTOR STATS for queue [" + ev.getProjectId() + "]: Tasks waiting in queue -> " + ev.getContent());
			Project p = this.projects.get(ev.getProjectId());
			p.setTasksQueued((int) ev.getContent());
		});
		hzClient.getTopic("task-completed").addMessageListener((message) -> {
			MyEvent ev = (MyEvent) message.getMessageObject();
			System.out.println("TASK [" + ev.getContent() + "] completed for project [" + ev.getProjectId() + "]");
			Project p = this.projects.get(ev.getProjectId());
			p.incrementTasksCompleted();
		});
	}

	public Project newProject(String id, String data) {
		Project p = new Project(id, data);
		this.projects.put(id, p);
		return p;
	}

	public void solveProject(Project p) throws Exception {
		IQueue<ProjectTask> queue = this.hzClient.getQueue(p.getId());
		this.hzClient.getTopic("new-project").publish(new MyEvent(p.getId(), "new-project", p.getId()));

		p.solve(queue, hzClient.getAtomicLong("countdown-" + p.getId()));
	}

	public Project getProject(String projectId) {
		return this.projects.get(projectId);
	}

}
