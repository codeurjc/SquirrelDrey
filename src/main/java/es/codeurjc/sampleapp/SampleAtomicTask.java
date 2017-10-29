package es.codeurjc.sampleapp;

import java.util.Map;
import com.hazelcast.core.IAtomicLong;
import es.codeurjc.distributed.algorithm.Task;

public class SampleAtomicTask extends Task<String> {

	private static final long serialVersionUID = 1L;
	private String resultMapId;
	private String atomicLongId;
	private String workData;
	private String workDescription;

	public SampleAtomicTask(String resultMapId, String atomicLongId, String workData, String workDescription) {
		this.resultMapId = resultMapId;
		this.atomicLongId = atomicLongId;
		this.workData = workData;
		this.workDescription = workDescription;
	}

	public String getWorkData() {
		return this.workData;
	}

	public String getWorkDescription() {
		return this.workDescription;
	}

	@Override
	public void process() throws Exception {
		Thread.sleep(6000);
		
		this.setResult("20");
		
		Map<Integer, Integer> results = hazelcastInstance.getMap(this.resultMapId);
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong(this.atomicLongId);
		results.put(Integer.parseInt(this.workDescription), Integer.parseInt(this.result));

		if (atomicLong.decrementAndGet() == 0L) {
			addNewTask(new SampleSolveTask());
		}
	}
	
}
