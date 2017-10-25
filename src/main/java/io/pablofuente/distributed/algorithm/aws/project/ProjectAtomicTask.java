package io.pablofuente.distributed.algorithm.aws.project;

import java.math.BigInteger;
import java.util.Map;

import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IQueue;

public class ProjectAtomicTask extends ProjectTask {

	private static final long serialVersionUID = 1L;
	private String resultMapId;
	private String atomicLongId;
	private String workData;
	private String workDescription;
	private String result;

	public ProjectAtomicTask(Project project, String resultMapId, String atomicLongId, String workData, String workDescription) {
		super(project);
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
	public String call() throws Exception {
		this.publishProjectStats();
		String digits = factorial(Integer.parseInt(workData)).toString();
		int sum = 0;
		for (int i = 0; i < digits.length(); i++) {
			int digit = (int) (digits.charAt(i) - '0');
			sum = sum + digit;
		}
		this.result = Integer.toString(sum);
		return Integer.toString(sum);
	}

	private BigInteger factorial(int n) throws InterruptedException {
		BigInteger result = BigInteger.ONE;
		for (; n > 1; n--) {
			result = result.multiply(BigInteger.valueOf(n));
		}
		return result;
	}

	@Override
	public String callback() {
		super.callback();
		System.out.println("> Atomic task [" + this.workDescription + "] finished. Result: " + this.result);
		Map<Integer, Integer> results = hazelcastInstance.getMap(this.resultMapId);
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong(this.atomicLongId);
		results.put(Integer.parseInt(this.workDescription), Integer.parseInt(this.result));

		if (atomicLong.decrementAndGet() == 0L) {
			IQueue<ProjectTask> queue = hazelcastInstance.getQueue(this.project.getId());
			queue.add(new ProjectSolveTask(this.project));
		}
		return this.result;
	}

}
