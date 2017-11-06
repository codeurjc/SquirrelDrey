package es.codeurjc.squirrel.drey;

import java.io.Serializable;

public class WorkerStats implements Serializable {

	private static final long serialVersionUID = 1L;

	String workerId;
	Integer totalCores;
	Integer workingCores;

	public WorkerStats(String workerId, int totalCores, int workingCores) {
		this.workerId = workerId;
		this.totalCores = totalCores;
		this.workingCores = workingCores;
	}

	public WorkerStats() {
	}

	public String getWorkerId() {
		return this.workerId;
	}

	public void setWorkerId(String workerId) {
		this.workerId = workerId;
	}

	public Integer getTotalCores() {
		return totalCores;
	}

	public void setTotalCores(Integer totalCores) {
		this.totalCores = totalCores;
	}

	public Integer getWorkingCores() {
		return workingCores;
	}

	public void setWorkingCores(Integer workingCores) {
		this.workingCores = workingCores;
	}
	
	@Override
	public String toString() {
		return "[workerId: " + this.workerId + ", totalCores: " + this.totalCores + ", workingCores: " + this.workingCores + "]";
	}

}
