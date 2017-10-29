package es.codeurjc.sampleapp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.IAtomicLong;

import es.codeurjc.distributed.algorithm.Task;

public class SamplePreparationTask extends Task<Void> {

	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(SamplePreparationTask.class);
	
	private String inputData;
	private String atomicLongId;

	public SamplePreparationTask(String inputData, String atomicLongId) {
		this.inputData = inputData;
		this.atomicLongId = atomicLongId;
	}

	@Override
	public void process() throws Exception {
		List<String> works = this.obtainWorksFromData(inputData);
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong(this.atomicLongId);
		List<SampleAtomicTask> atomicTasks = this.generateAtomicTasks(works);
		atomicLong.set(atomicTasks.size());
		for (SampleAtomicTask t : atomicTasks) {
			try {
				addNewTask(t);
				publishQueueStats();
			} catch (Exception e) {
				log.error("Error while processing task [" + t.getWorkDescription() + "]");
				e.printStackTrace();
			}
		}
	}

	private List<String> obtainWorksFromData(String data) {
		long hash = 0;
		for (char c : data.toCharArray()) {
			hash = 31L * hash + c;
		}
		Random random = new Random(hash);
		BigInteger i = new BigInteger(256, random);

		List<String> s = new ArrayList<String>(Arrays.asList(i.toString().split("(?<=\\G.{5})")));
		s.addAll(s);

		log.info("NUMBER OF TASKS TO EXECUTE IN ALGORITHM [{}]: {}", this.algorithm.getId(), s.size());
		return s;
	}

	private List<SampleAtomicTask> generateAtomicTasks(List<String> works) {
		List<SampleAtomicTask> atomicTasks = new ArrayList<>();
		for (int i = 0; i < works.size(); i++) {
			SampleAtomicTask t = new SampleAtomicTask("results-" + this.algorithm.getId(), this.atomicLongId, works.get(i), Integer.toString(i));
			atomicTasks.add(t);
		}
		return atomicTasks;
	}

}
