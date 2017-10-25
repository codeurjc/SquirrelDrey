package io.pablofuente.distributed.algorithm.aws.project;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.ItemEvent;
import com.hazelcast.core.ItemListener;

public class QueueListener implements ItemListener<ProjectTask> {

	IQueue<ProjectTask> queue;
	ExecutorService executor;
	Map<String, QueueListener> listeners;
	HazelcastInstance hazelcastInstance;
	String id;

	public QueueListener(IQueue<ProjectTask> queue, ExecutorService executor, Map<String, QueueListener> listeners,
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
		System.out.println("Item [" + item.getItem().toString() + "] added to queue [" + this.queue.getName() + "]");
		ProjectTask task = queue.poll();
		if (task != null) {
			CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
				try {
					return task.call();
				} catch (Exception e) {
					return "ERROR: " + e;
				}
			}, executor);
			future.thenAcceptAsync(result -> task.callback(), executor);
		}
	}

	@Override
	public void itemRemoved(ItemEvent<ProjectTask> item) {
		System.out.println("Item [" + item.getItem().toString() + "] removed from queue [" + this.queue.getName()
				+ "] by member [" + item.getMember() + "]");
	}
}