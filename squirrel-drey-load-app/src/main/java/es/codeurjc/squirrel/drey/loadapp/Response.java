package es.codeurjc.squirrel.drey.loadapp;

import java.util.List;

import es.codeurjc.squirrel.drey.WorkerStats;

public class Response {

	AlgorithmStats algorithmStats;
	List<WorkerStats> workerStats;

	public Response(AlgorithmStats algorithmStats, List<WorkerStats> workerStats) {
		this.algorithmStats = algorithmStats;
		this.workerStats = workerStats;
	}

	public AlgorithmStats getAlgorithmStats() {
		return this.algorithmStats;
	}

	public void setAlgorithmStats(AlgorithmStats algorithmStats) {
		this.algorithmStats = algorithmStats;
	}

	public List<WorkerStats> getWorkerStats() {
		return this.workerStats;
	}

	public void setWorkerStats(List<WorkerStats> workerStats) {
		this.workerStats = workerStats;
	}

}
