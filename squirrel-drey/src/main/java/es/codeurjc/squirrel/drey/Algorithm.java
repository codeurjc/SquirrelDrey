package es.codeurjc.squirrel.drey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;
import com.hazelcast.spi.exception.DistributedObjectDestroyedException;

/**
 * @author Pablo Fuente (pablofuenteperez@gmail.com)
 */
public class Algorithm<R> {

	public enum Status {
		/**
		 * Algorithm has started (method
		 * {@link AlgorithmManager#solveAlgorithm(String, Task, Integer)} has been
		 * called)
		 */
		STARTED,
		/**
		 * Algorithm has successfully finished
		 */
		COMPLETED,
		/**
		 * Algorithm has been manually cancelled by calling any of the termination
		 * methods of {@link AlgorithmManager}
		 */
		TERMINATED,
		/**
		 * Algorithm has been forcibly finished by a task that didn't manage to complete
		 * within its specified timeout
		 */
		TIMEOUT
	}

	HazelcastInstance hc;

	private String id;
	private Integer priority;
	private Status status;

	private R result;

	private int finalTasksAdded;
	private int finalTasksCompleted;
	private int finalTasksQueued;
	private int finalTasksTimeout;
	private AtomicBoolean finished = new AtomicBoolean(false);

	private Long initTime;
	private Long finishTime;

	private Task initialTask;
	private List<Task> errorTasks;
	private Consumer<R> callback;
	private AlgorithmCallback<R> algorithmCallback;

	public Algorithm(HazelcastInstance hc, String id, Integer priority, Task initialTask) {
		this.hc = hc;
		this.id = id;
		this.priority = priority;
		initialTask.setAlgorithm(this.getId());
		this.initialTask = initialTask;
		this.errorTasks = new ArrayList<>();
	}

	public Algorithm(HazelcastInstance hc, String id, Integer priority, Task initialTask, Consumer<R> callback) {
		this.hc = hc;
		this.id = id;
		this.priority = priority;
		initialTask.setAlgorithm(this.getId());
		this.initialTask = initialTask;
		this.callback = callback;
		this.errorTasks = new ArrayList<>();
	}

	public Algorithm(HazelcastInstance hc, String id, Integer priority, Task initialTask,
			AlgorithmCallback<R> callback) {
		this.hc = hc;
		this.id = id;
		this.priority = priority;
		initialTask.setAlgorithm(this.getId());
		this.initialTask = initialTask;
		this.algorithmCallback = callback;
		this.errorTasks = new ArrayList<>();
	}

	public Algorithm(HazelcastInstance hc, String id, Algorithm<R> otherAlgorithm) {
		this.hc = hc;
		this.id = id;
		this.priority = otherAlgorithm.getPriority();
		this.initialTask = otherAlgorithm.getInitialTask();
		this.initialTask.setAlgorithm(this.getId());
		if (otherAlgorithm.callback != null) {
			this.callback = otherAlgorithm.callback;
		} else if (otherAlgorithm.algorithmCallback != null) {
			this.algorithmCallback = otherAlgorithm.algorithmCallback;
		}
		this.errorTasks = new ArrayList<>();
	}

	public String getId() {
		return this.id;
	}

	public Integer getPriority() {
		return this.priority;
	}

	public Status getStatus() {
		return this.status;
	}

	void setStatus(Status status) {
		this.status = status;
	}

	public void solve(IQueue<Task> queue) throws Exception {
		this.status = Status.STARTED;
		this.initTime = System.currentTimeMillis();

		this.initialTask.status = Task.Status.QUEUED;
		queue.add(this.initialTask);

		this.hc.getCPSubsystem().getAtomicLong("added" + this.id).incrementAndGet();
	}

	public int getTasksAdded() throws DistributedObjectDestroyedException {
		if (this.finished.get()) {
			return this.finalTasksAdded;
		}
		return Math.toIntExact(this.hc.getCPSubsystem().getAtomicLong("added" + this.id).get());
	}

	public int getTasksCompleted() throws DistributedObjectDestroyedException {
		if (this.finished.get()) {
			return this.finalTasksCompleted;
		}
		return Math.toIntExact(this.hc.getCPSubsystem().getAtomicLong("completed" + this.id).get());
	}

	public int getTasksQueued() throws DistributedObjectDestroyedException {
		if (this.finished.get()) {
			return this.finalTasksQueued;
		}
		return this.hc.getQueue(this.id).size();
	}

	public int getTasksTimeout() throws DistributedObjectDestroyedException {
		if (this.finished.get()) {
			return this.finalTasksTimeout;
		}
		return Math.toIntExact(this.hc.getCPSubsystem().getAtomicLong("timeout" + this.id).get());
	}

	public int getTasksFinished() {
		return this.getTasksCompleted() + this.getTasksTimeout();
	}

	public R getResult() {
		return result;
	}

	public void setResult(R result) {
		this.result = result;
	}

	public Long getTimeOfProcessing() {
		if (this.finishTime != null) {
			return (this.finishTime - this.initTime) / 1000;
		} else {
			return 0L;
		}
	}

	public Task getInitialTask() {
		return this.initialTask;
	}

	public List<Task> getErrorTasks() {
		return this.errorTasks;
	}

	public void addErrorTask(Task errorTask) {
		this.errorTasks.add(errorTask);
	}

	public void runCallbackSuccess() throws Exception {
		this.finishTime = System.currentTimeMillis();
		this.status = Status.COMPLETED;
		if (this.callback != null) {
			this.callback.accept(this.result);
		} else if (this.algorithmCallback != null) {
			this.algorithmCallback.onSuccess(this.result, this);
		}
	}

	public void runCallbackError(Status status) {
		this.finishTime = System.currentTimeMillis();
		this.status = status;
		if (this.algorithmCallback != null) {
			this.algorithmCallback.onError(this);
		}
	}

	public boolean hasSuccessullyFinished(Long numberOfTaskCompletedEvents) {
		final int tasksAdded = this.getTasksAdded();
		final int tasksCompleted = this.getTasksCompleted();
		final int tasksQueued = this.getTasksQueued();
		final int tasksTimeout = this.getTasksTimeout();
		boolean hasSuccessullyFinished = (tasksAdded == tasksCompleted) && (tasksTimeout == 0)
				&& (tasksCompleted == numberOfTaskCompletedEvents) && (tasksQueued == 0);
		if (hasSuccessullyFinished) {
			this.finalTasksAdded = tasksAdded;
			this.finalTasksCompleted = tasksCompleted;
			this.finalTasksQueued = tasksQueued;
			this.finalTasksTimeout = tasksTimeout;
			this.finished.compareAndSet(false, true);
		}
		return hasSuccessullyFinished;
	}

	public boolean hasFinishedRunningTasks(int addedMinusQueued) {
		final int finished = this.getTasksFinished();
		System.out.println("Added minus queued: " + addedMinusQueued);
		System.out.println("Tasks finished: " + finished);
		return finished == addedMinusQueued;
	}

	@Override
	public int hashCode() {
		return this.id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return (this.id.equals(((Algorithm<?>) o).id));
	}

}
