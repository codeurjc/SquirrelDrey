package io.pablofuente.distributed.algorithm.aws.project;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;

public class ProjectPreparationTask extends ProjectTask {

	private static final long serialVersionUID = 1L;
	private transient IMap<Integer, Integer> results;
	private String atomicLongId;
	
	@Override
	public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
		super.setHazelcastInstance(hazelcastInstance);
		this.results = hazelcastInstance.getMap("results-" + project.getId());
	}

	public ProjectPreparationTask(Project project) {
		super(project);
	}

	@Override
	public String call() throws Exception {
		super.call();
		List<String> works = this.obtainWorksFromData(project.getData());
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong(this.atomicLongId);
		List<ProjectAtomicTask> atomicTasks = this.generateAtomicTasks(works);
		atomicLong.set(atomicTasks.size());
		for (ProjectAtomicTask t : atomicTasks) {
			try {
				solveAtomicTask(t);
			} catch (Exception e) {
				System.err.println("Error while processing task [" + t.getWorkDescription() + "]");
				e.printStackTrace();
			}
		}
		return "";
	}

	private void solveAtomicTask(ProjectAtomicTask task) throws Exception {
		IQueue<ProjectTask> queue = hazelcastInstance.getQueue(this.project.getId());
		queue.add(task);

		publishQueueStats();
	}

	private List<String> obtainWorksFromData(String data) {

		long hash = 0;
		for (char c : data.toCharArray()) {
			hash = 31L * hash + c;
		}
		Random random = new Random(hash);
		BigInteger i = new BigInteger(256, random);

		System.out.println("> Number to process: " + i);

		List<String> s = new ArrayList<String>(Arrays.asList(i.toString().split("(?<=\\G.{5})")));
		s.addAll(s);

		System.out.println("> Tasks to execute: " + s);
		return s;
	}

	private List<ProjectAtomicTask> generateAtomicTasks(List<String> works) {
		List<ProjectAtomicTask> atomicTasks = new ArrayList<>();
		for (int i = 0; i < works.size(); i++) {
			atomicTasks.add(new ProjectAtomicTask(this.project, this.results.getName(), this.atomicLongId, works.get(i),
					Integer.toString(i)));
		}
		return atomicTasks;
	}

	public void setAtomicLong(IAtomicLong atomicLong) {
		this.atomicLongId = atomicLong.getName();
	}

}
