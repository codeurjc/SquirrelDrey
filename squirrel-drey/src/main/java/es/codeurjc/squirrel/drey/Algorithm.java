package es.codeurjc.squirrel.drey;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;

/**
 * @author Pablo Fuente (pablo.fuente@urjc.es)
 */
public class Algorithm<R> {

	HazelcastInstance hc;

	private String id;
	private Integer priority;

	private R result;

	private int finalTasksAdded;
	private int finalTasksCompleted;
	private int finalTasksQueued;
	private AtomicBoolean finished = new AtomicBoolean(false);

	private Long initTime;
	private Long finishTime;

	private Task initialTask;
	private Consumer<R> callback;

	public Algorithm(HazelcastInstance hc, String id, Integer priority, Task initialTask) {
		this.hc = hc;
		this.id = id;
		this.priority = priority;

		initialTask.setAlgorithm(this.getId());
		this.initialTask = initialTask;
	}

	public Algorithm(HazelcastInstance hc, String id, Integer priority, Task initialTask, Consumer<R> callback) {
		this.hc = hc;
		this.id = id;
		this.priority = priority;

		initialTask.setAlgorithm(this.getId());
		this.initialTask = initialTask;
		this.callback = callback;
	}

	public String getId() {
		return this.id;
	}

	public Integer getPriority() {
		return this.priority;
	}

	public void solve(IQueue<Task> queue) throws Exception {
		this.initTime = System.currentTimeMillis();
		queue.add(this.initialTask);

		this.hc.getAtomicLong("added" + this.id).incrementAndGet();
	}

	public int getTasksAdded() {
		if (this.finished.get()) {
			return this.finalTasksAdded;
		}
		return Math.toIntExact(this.hc.getAtomicLong("added" + this.id).get());
	}

	public int getTasksCompleted() {
		if (this.finished.get()) {
			return this.finalTasksCompleted;
		}
		return Math.toIntExact(this.hc.getAtomicLong("completed" + this.id).get());
	}

	public int getTasksQueued() {
		if (this.finished.get()) {
			return this.finalTasksQueued;
		}
		return this.hc.getQueue(this.id).size();
	}

	public R getResult() {
		return result;
	}

	public void setResult(R result) {
		this.result = result;
	}

	public void setFinishTime(long finishTime) {
		this.finishTime = finishTime;
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

	public void runCallback() throws Exception {
		if (this.callback != null) {
			this.callback.accept(this.result);
		}
	}

	public boolean hasFinished(Task t, Long numberOfTaskCompletedEvents) {
		boolean hasFinished = (this.hc.getAtomicLong("added" + this.id).get() == t.getTasksCompleted())
				&& (t.getTasksCompleted() == numberOfTaskCompletedEvents) && (this.getTasksQueued() == 0);
		if (hasFinished) {
			this.finalTasksAdded = Math.toIntExact(this.hc.getAtomicLong("added" + this.id).get());
			this.finalTasksCompleted = Math.toIntExact(t.getTasksCompleted());
			this.finalTasksQueued = this.hc.getQueue(this.id).size();
			this.finished.compareAndSet(false, true);
		}
		return hasFinished;
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
