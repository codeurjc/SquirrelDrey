package io.pablofuente.distributed.algorithm.aws.worker;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.ItemEvent;
import com.hazelcast.core.ItemListener;

import io.pablofuente.distributed.algorithm.aws.project.ProjectTask;

public class QueueListener implements ItemListener<ProjectTask> {

	IQueue<ProjectTask> queue;
	ThreadPoolExecutor executor;
	Map<String, QueueListener> listeners;
	HazelcastInstance hazelcastInstance;
	AtomicBoolean subscribed = new AtomicBoolean(true);
	String id;

	public QueueListener(IQueue<ProjectTask> queue, ThreadPoolExecutor executor, Map<String, QueueListener> listeners,
			HazelcastInstance hazelcastInstance) {
		this.queue = queue;
		this.executor = executor;
		this.listeners = listeners;
		this.hazelcastInstance = hazelcastInstance;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Override
	public void itemAdded(ItemEvent<ProjectTask> item) {
		synchronized (this) {
			System.out.println("Item [" + item.getItem().toString() + "] added to queue [" + this.queue.getName()
					+ "] by member [" + item.getMember() + "]");
			int activeTasks = executor.getActiveCount();
			System.out.println("ACTIVE TASKS: " + activeTasks);
			if (activeTasks < Runtime.getRuntime().availableProcessors()) {
				ProjectTask task = queue.poll();
				if (task != null) {
					runTask(task);
				}
			} else if (this.subscribed.get()) {
				this.unsubscribeFromAllListeners();
			}
		}
	}

	@Override
	public void itemRemoved(ItemEvent<ProjectTask> item) {
		System.out.println("Item [" + item.getItem().toString() + "] removed from queue [" + this.queue.getName()
				+ "] by member [" + item.getMember() + "]");
	}

	private void runTask(ProjectTask task) {
		CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
			try {
				return task.call();
			} catch (Exception e) {
				return "ERROR: " + e;
			}
		}, executor);
		future.thenAcceptAsync(result -> {
			task.callback();
			if (!subscribed.get())
				resumeTasks();
		}, executor);
	}

	private synchronized void unsubscribeFromAllListeners() {
		System.out.println("UNSUBSCRIBING from queue [" + this.queue.getName() + "]");
		for (QueueListener l : this.listeners.values()) {
			this.queue.removeItemListener(l.id);
		}
		this.subscribed.set(false);
	}

	private synchronized void resumeTasks() {
		ProjectTask task = queue.poll();
		if (task != null) {
			runTask(task);
		} else {
			System.out.println("SUBSCRIBING to queue [" + this.queue.getName() + "]");
			for (QueueListener l : this.listeners.values()) {
				this.queue.addItemListener(l, true);
			}
			this.subscribed.set(true);
		}
	}
}