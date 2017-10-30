package es.codeurjc.distributed.algorithm;

public class WorkerStats {
	
	String id;
	Integer totalCores;
	Integer workingCores;
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
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

}
