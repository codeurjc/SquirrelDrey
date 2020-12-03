package es.codeurjc.squirrel.drey.local;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

public class WorkerStats implements Serializable {

	private static final Logger log = LoggerFactory.getLogger(WorkerStats.class);

	private static final long serialVersionUID = 1L;

	/**
	 * Time of worker creation
	 */
	long launchingTime;

	/**
	 * Moment where the stat was taken
	 */
	long timeDataFetch;

	/**
	 * EC2 Instance Id
	 */
	String ec2InstanceId;

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
	long taskRunning;

	/**
	 * Worker status
	 */
	WorkerStatus status;

	public WorkerStats(long launchingTime, String ec2InstanceId, String workerId, String directQueueUrl, int totalCores, int workingCores, long tasksAdded,
					   long totalCompletedTasks, long taskRunning, WorkerStatus status) {
		this.launchingTime = launchingTime;
		this.ec2InstanceId = ec2InstanceId;
		this.timeDataFetch = System.currentTimeMillis();
		this.workerId = workerId;
		this.directQueueUrl = directQueueUrl;
		this.totalCores = totalCores;
		this.workingCores = workingCores;
		this.tasksAdded = tasksAdded;
		this.totalCompletedTasks = totalCompletedTasks;
		this.taskRunning = taskRunning;
		this.status = status;
	}

	public WorkerStats(long launchingTime, String workerId, String directQueueUrl, int totalCores, int workingCores, long tasksAdded, long totalCompletedTasks,
					   long taskRunning, WorkerStatus status) {
		this.launchingTime = launchingTime;
		this.timeDataFetch = System.currentTimeMillis();
		this.workerId = workerId;
		this.directQueueUrl = directQueueUrl;
		this.totalCores = totalCores;
		this.workingCores = workingCores;
		this.tasksAdded = tasksAdded;
		this.totalCompletedTasks = totalCompletedTasks;
		this.taskRunning = taskRunning;
		this.status = status;
	}

	public long getLaunchingTime() {
		return launchingTime;
	}

	public void setLaunchingTime(long launchingTime) {
		this.launchingTime = launchingTime;
	}

	public long getTimeDataFetch() {
		return timeDataFetch;
	}

	public void setTimeDataFetch(long timeDataFetch) {
		this.timeDataFetch = timeDataFetch;
	}

	public String getEc2InstanceId() {
		return ec2InstanceId;
	}

	public void setEc2InstanceId(String ec2InstanceId) {
		this.ec2InstanceId = ec2InstanceId;
	}

	public String getInstanceId() {
		return this.getInstanceId();
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

	public long getTaskRunning() {
		return taskRunning;
	}

	public void setTaskRunning(long taskRunning) {
		this.taskRunning = taskRunning;
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

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		if (launchingTime != 0L) {
			json.addProperty("creationTime", launchingTime);
		}
		if (timeDataFetch != 0L) {
			json.addProperty("timeDataFetch", timeDataFetch);
		}
		if (ec2InstanceId != null) {
			json.addProperty("ec2InstanceId", ec2InstanceId);
		}
		if (workerId != null) {
			json.addProperty("workerId", workerId);
		}
		json.addProperty("directQueueUrl", directQueueUrl);
		json.addProperty("totalCores", totalCores);
		json.addProperty("workingCores", totalCores);
		json.addProperty("tasksAdded", totalCores);
		json.addProperty("totalCompletedTasks", totalCores);
		json.addProperty("taskRunning", taskRunning);
		json.addProperty("status", status.name());
		return json;
	}

	@Override
	public String toString() {
		return "WorkerStats [" +
				"creationTime=" + launchingTime +
				", timeDataFetch=" + timeDataFetch +
				", ec2InstanceId='" + ec2InstanceId + '\'' +
				", workerId='" + workerId + '\'' +
				", directQueueUrl='" + directQueueUrl + '\'' +
				", totalCores=" + totalCores +
				", workingCores=" + workingCores +
				", tasksAdded=" + tasksAdded +
				", totalCompletedTasks=" + totalCompletedTasks +
				", taskRunning=" + taskRunning +
				", status=" + status + "]";
	}
}
