package es.codeurjc.squirrel.drey.local;

import com.google.gson.JsonObject;

import java.io.Serializable;
import java.util.Objects;

public class WorkerStats implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Time of worker creation
	 */
	long launchingTime;

	/**
	 * Moment where the stat was taken
	 */
	long lastTimeFetched;

	/**
	 * Reprensents the seconds worker is idle since taskRunning=0
	 */
	long lastTimeWorking;

	/**
	 * EC2 Instance Id
	 */
	String environmentId;

	/**
	 * Worker internal id
	 */
	String workerId;

	/**
	 * Direct queue url of the worker
	 */
	String directQueueUrl;

	/**
	 * Total num of cores
	 */
	int totalCores;

	/**
	 * Total num of available cores to do tasks
	 */
	int workingCores;

	/**
	 * Total task added into the worker
	 */
	long tasksAdded;

	/**
	 * Total completed tasks by the worker
	 */
	long totalCompletedTasks;

	/**
	 * Total tasks actually running
	 */
	long tasksRunning;

	/**
	 * Worker status
	 */
	WorkerStatus status;

	/**
	 * Did not respond last fetch
	 */
	boolean isDisconnected;

	/**
	 * Time taken from launching to running in seconds
	 */
	double timeToLaunch;

	Integer parallelizationGrade;

	public WorkerStats(long launchingTime, String environmentId, long lastTimeWorking, String workerId, String directQueueUrl, int totalCores, int workingCores, long tasksAdded,
					   long totalCompletedTasks, long tasksRunning, Integer parallelizationGrade, WorkerStatus status) {
		this.launchingTime = launchingTime;
		this.environmentId = environmentId;
		this.lastTimeWorking = lastTimeWorking;
		this.lastTimeFetched = System.currentTimeMillis();
		this.workerId = workerId;
		this.directQueueUrl = directQueueUrl;
		this.totalCores = totalCores;
		this.workingCores = workingCores;
		this.tasksAdded = tasksAdded;
		this.totalCompletedTasks = totalCompletedTasks;
		this.tasksRunning = tasksRunning;
		this.status = status;
		this.isDisconnected = false;
		this.parallelizationGrade = parallelizationGrade;
	}

	public long getLaunchingTime() {
		return launchingTime;
	}

	public void setLaunchingTime(long launchingTime) {
		this.launchingTime = launchingTime;
	}

	public double getTimeToLaunch() {
		return this.timeToLaunch;
	}

	public void setTimeToLaunch(double timeToLaunch) {
		this.timeToLaunch = timeToLaunch;
	}

	public long getLastTimeFetched() {
		return lastTimeFetched;
	}

	public void setLastTimeFetched(long lastTimeFetched) {
		this.lastTimeFetched = lastTimeFetched;
	}

	public long getLastTimeWorking() {
		return lastTimeWorking;
	}

	public void setLastTimeWorking(long lastTimeWorking) {
		this.lastTimeWorking = lastTimeWorking;
	}

	public String getEnvironmentId() {
		return environmentId;
	}

	public void setEnvironmentId(String environmentId) {
		this.environmentId = environmentId;
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

	public long getTasksAdded() {
		return tasksAdded;
	}

	public void setTasksAdded(long tasksAdded) {
		this.tasksAdded = tasksAdded;
	}

	public long getTotalCompletedTasks() {
		return totalCompletedTasks;
	}

	public void setTotalCompletedTasks(long totalCompletedTasks) {
		this.totalCompletedTasks = totalCompletedTasks;
	}

	public long getTasksRunning() {
		return tasksRunning;
	}

	public void setTasksRunning(long tasksRunning) {
		this.tasksRunning = tasksRunning;
	}

	public WorkerStatus getStatus() {
		return status;
	}

	public void setStatus(WorkerStatus status) {
		this.status = status;
	}

	public String getDirectQueueUrl() {
		return directQueueUrl;
	}

	public void setDirectQueueUrl(String directQueueUrl) {
		this.directQueueUrl = directQueueUrl;
	}

	public void setTotalCores(int totalCores) {
		this.totalCores = totalCores;
	}

	public void setWorkingCores(int workingCores) {
		this.workingCores = workingCores;
	}

	public boolean isDisconnected() {
		return isDisconnected;
	}

	public void setDisconnected(boolean disconnected) {
		isDisconnected = disconnected;
	}

	public Integer getParallelizationGrade() {
		return this.parallelizationGrade;
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		if (launchingTime != 0L) {
			json.addProperty("creationTime", launchingTime);
		}
		if (lastTimeFetched != 0L) {
			json.addProperty("timeDataFetch", lastTimeFetched);
		}
		if (lastTimeWorking != 0L) {
			json.addProperty("secondsIdle", lastTimeWorking);
		}
		if (environmentId != null) {
			json.addProperty("environmentId", environmentId);
		}
		if (workerId != null) {
			json.addProperty("workerId", workerId);
		}
		json.addProperty("directQueueUrl", directQueueUrl);
		json.addProperty("totalCores", totalCores);
		json.addProperty("workingCores", totalCores);
		json.addProperty("tasksAdded", totalCores);
		json.addProperty("totalCompletedTasks", totalCores);
		json.addProperty("taskRunning", tasksRunning);
		json.addProperty("status", status.name());
		json.addProperty("parallelizationGrade", parallelizationGrade);
		return json;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		WorkerStats that = (WorkerStats) o;
		return launchingTime == that.launchingTime
				&& lastTimeFetched == that.lastTimeFetched && lastTimeWorking == that.lastTimeWorking
				&& totalCores == that.totalCores && workingCores == that.workingCores && tasksAdded == that.tasksAdded
				&& totalCompletedTasks == that.totalCompletedTasks && tasksRunning == that.tasksRunning
				&& Objects.equals(parallelizationGrade, that.parallelizationGrade)
				&& Objects.equals(environmentId, that.environmentId) && Objects.equals(workerId, that.workerId)
				&& Objects.equals(directQueueUrl, that.directQueueUrl) && status == that.status;
	}

	@Override
	public int hashCode() {
		return Objects.hash(launchingTime, lastTimeFetched, lastTimeWorking, environmentId, workerId, directQueueUrl,
				totalCores, workingCores, tasksAdded, totalCompletedTasks, parallelizationGrade, tasksRunning, status);
	}

	@Override
	public String toString() {
		return "WorkerStats[ " +
				"launchingTime=" + launchingTime +
				", lastTimeFetched=" + lastTimeFetched +
				", lastTimeWorking=" + lastTimeWorking +
				", environmentId='" + environmentId + '\'' +
				", workerId='" + workerId + '\'' +
				", directQueueUrl='" + directQueueUrl + '\'' +
				", totalCores=" + totalCores +
				", workingCores=" + workingCores +
				", tasksAdded=" + tasksAdded +
				", totalCompletedTasks=" + totalCompletedTasks +
				", tasksRunning=" + tasksRunning +
				", status=" + status +
				", isDisconnected=" + isDisconnected +
				", timeToLaunch=" + timeToLaunch +
				", parallelizationGrade=" + parallelizationGrade + " ]";
	}
}
