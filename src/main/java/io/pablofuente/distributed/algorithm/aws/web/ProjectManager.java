package io.pablofuente.distributed.algorithm.aws.web;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;

import io.pablofuente.distributed.algorithm.aws.project.MyEvent;
import io.pablofuente.distributed.algorithm.aws.project.Project;
import io.pablofuente.distributed.algorithm.aws.project.ProjectTask;

public class ProjectManager {
	
	private HazelcastInstance hzClient;
	private Map<String, Project> projects;
	private IMap<String, String> QUEUES;
	
	public ProjectManager(String HAZELCAST_CLIENT_CONFIG) {
		
		ClientConfig config = new ClientConfig();
		try {
			config = new XmlClientConfigBuilder(HAZELCAST_CLIENT_CONFIG).build();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.hzClient = HazelcastClient.newHazelcastClient(config);
		
		this.hzClient.getCluster().addMembershipListener(new ClusterMembershipListener());

		this.projects = new ConcurrentHashMap<>();
		this.QUEUES = this.hzClient.getMap("QUEUES");

		hzClient.getTopic("project-solved").addMessageListener((message) -> {
			MyEvent ev = (MyEvent) message.getMessageObject();
			System.out.println("PROJECT SOLVED: Project: " + ev.getProjectId() + ", Result: " + ev.getContent());
			Project p = this.projects.get(ev.getProjectId());
			p.setFinishTime(System.currentTimeMillis());
			p.setResult(Integer.toString((int) ev.getContent()));
		});
		hzClient.getTopic("queue-stats").addMessageListener((message) -> {
			MyEvent ev = (MyEvent) message.getMessageObject();
			System.out.println("EXECUTOR STATS for queue [" + ev.getProjectId() + "]: Tasks waiting in queue -> "
					+ ev.getContent());
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
		QUEUES.put(p.getId(), queue.getName());
		
		p.solve(queue, hzClient.getAtomicLong("countdown-" + p.getId()));
	}

	public Project getProject(String projectId) {
		return this.projects.get(projectId);
	}

}
