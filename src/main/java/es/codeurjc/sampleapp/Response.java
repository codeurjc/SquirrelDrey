package es.codeurjc.sampleapp;

import java.util.List;

import es.codeurjc.distributed.algorithm.WorkerStats;

public class Response {
	
	List<AlgorithmStats> algorithmStats;
	List<WorkerStats> workerStats;

	public Response(List<AlgorithmStats> algorithmStats, List<WorkerStats> workerStats) {
		this.algorithmStats = algorithmStats;
		this.workerStats = workerStats;
	}
	
	public List<AlgorithmStats> getAlgorithmStats() {
		return this.algorithmStats;
	}
	
	public void setAlgorithmStats(List<AlgorithmStats> algorithmStats) {
		this.algorithmStats = algorithmStats;
	}
	
	public List<WorkerStats> getWorkerStats() {
		return this.workerStats;
	}
	
	public void setWorkerStats(List<WorkerStats> workerStats) {
		this.workerStats = workerStats;
	}

}
