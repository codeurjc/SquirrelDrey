package es.codeurjc.sampleapp;

import java.util.Map;
import com.hazelcast.core.IAtomicLong;
import es.codeurjc.distributed.algorithm.Task;

public class SampleAtomicTask extends Task<String> {

	private static final long serialVersionUID = 1L;
	private String resultMapId;
	private String atomicLongId;
	private String workData;
	private Integer taskDuration;
	private String workDescription;

	public SampleAtomicTask(String resultMapId, String atomicLongId, String workData, Integer taskDuration, String workDescription) {
		this.resultMapId = resultMapId;
		this.atomicLongId = atomicLongId;
		this.workData = workData;
		this.taskDuration = taskDuration;
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
		Thread.sleep(taskDuration * 1000);
		
		this.setResult("10");
		
		Map<Integer, Integer> results = hazelcastInstance.getMap(this.resultMapId);
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong(this.atomicLongId);
		results.put(Integer.parseInt(this.workDescription), Integer.parseInt(this.result));
		
		long l = atomicLong.decrementAndGet();
		
		App.logger.info("Must finish {} atomic tasks for algorithm {} yet", l, this.algorithmId );
		
		if (l == 0L) {
			App.logger.info("ADDING SOLVE TASK FOR ALGORITHM [{}]", this.algorithmId);
			addNewTask(new SampleSolveTask());
		}
	}
	
}
