package es.codeurjc.squirrel.drey.sampleapp.task;

import java.util.Map;
import com.hazelcast.core.IAtomicLong;

import es.codeurjc.squirrel.drey.Task;
import es.codeurjc.squirrel.drey.sampleapp.App;

public class AtomicTask extends Task {

	private static final long serialVersionUID = 1L;
	private String resultMapId;
	private String atomicLongId;
	private String workData;
	private Integer taskDuration;
	private String workDescription;

	public AtomicTask(String resultMapId, String atomicLongId, String workData, Integer taskDuration, String workDescription) {
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
		
		Map<Integer, Integer> results = hazelcastInstance.getMap(this.resultMapId);
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong(this.atomicLongId);
		results.put(Integer.parseInt(this.workDescription), 1);
				
		if (atomicLong.decrementAndGet() == 0L) {
			App.logger.info("ADDING SOLVE TASK FOR ALGORITHM [{}]", this.algorithmId);
			addNewTask(new ResultTask());
			atomicLong.destroy();
		}
	}
	
}
