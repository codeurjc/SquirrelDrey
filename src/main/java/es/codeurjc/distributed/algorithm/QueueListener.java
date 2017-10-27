package es.codeurjc.distributed.algorithm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hazelcast.core.IQueue;
import com.hazelcast.core.ItemEvent;
import com.hazelcast.core.ItemListener;

import es.codeurjc.sampleapp.App;

public class QueueListener implements ItemListener<Task<?>> {

	IQueue<Task<?>> queue;
	String id;
	QueuesManager manager;
	
	Map<String, Boolean> addEventChecking = new ConcurrentHashMap<>();
	Map<String, Boolean> removedEventChecking = new ConcurrentHashMap<>();

	public QueueListener(IQueue<Task<?>> queue, QueuesManager manager) {
		this.queue = queue;
		this.manager = manager;
	}
	
	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Override
	public void itemAdded(ItemEvent<Task<?>> item) {
		if (this.addEventChecking.putIfAbsent(item.getItem().toString(), true) != null) {
			App.logger.error("DUPLICATE ADD OPERATION FOR ITEM [" + item.getItem().toString() + "]");
		}

		App.logger.info("Item [" + item.getItem().toString() + "] added to queue [" + this.queue.getName()
				+ "] by member [" + item.getMember() + "]");
		
		manager.performOrderedPolling();
		//this.checkQueue();
	}

	@Override
	public void itemRemoved(ItemEvent<Task<?>> item) {
		if (this.removedEventChecking.putIfAbsent(item.getItem().toString(), true) != null) {
			App.logger.error("DUPLICATE REMOVE OPERATION FOR ITEM [" + item.getItem().toString() + "]");
		}

		App.logger.info("Item [" + item.getItem().toString() + "] removed from queue [" + this.queue.getName()
				+ "] by member [" + item.getMember() + "]");
	}

	/*public synchronized boolean checkQueue() {
		int activeTasks = executor.getActiveCount();
		System.out.println("ACTIVE TASKS: " + activeTasks);
		if (activeTasks < Runtime.getRuntime().availableProcessors()) {
			ProjectTask task = queue.poll();
			System.out.println("TRYING TASK: " + task);
			if (task != null) {
				System.out.println("EXECUTING TASK: " + task);
				runTask(task);
				return true;
			}
		} else {
			this.unsubscribeFromAllListeners();
		}
		return false;
	}*/

	/*private void runTask(ProjectTask task) {
		CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
			try {
				return task.call();
			} catch (Exception e) {
				return "ERROR: " + e;
			}
		}, executor);
		future.thenAcceptAsync(result -> {
			try {
				System.out.println("CALLBAAAAAACK");
				task.callback();
				System.out.println("CALLBAAAAAACK22222");
				resumeOrderedTasks();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, executor);
	}*/

	/*private synchronized void unsubscribeFromAllListeners() {
		System.out.println("UNSUBSCRIBING from queue [" + this.queue.getName() + "]");
		for (QueueListener l : this.listeners.values()) {
			this.queue.removeItemListener(l.id);
		}
		// this.subscribed.set(false);
	}*/

	/*private synchronized void resumeOrderedTasks() {

		int activeTasks = executor.getActiveCount();
		if (activeTasks < Runtime.getRuntime().availableProcessors()) {
			System.out.println("TENGO HUECO");
		} else {
			System.out.println("NO TENGO HUECO");
		}

		Map<String, Integer> m = QueuesManager.sortMapByValue(this.hazelcastInstance.getMap("QUEUES"));
		boolean taskAvailable = false;
		for (String queueId : m.keySet()) {
			IQueue<ProjectTask> q = this.hazelcastInstance.getQueue(queueId);
			ProjectTask task = q.poll();
			if (task != null) {
				runTask(task);
				taskAvailable = true;
				break;
			}
		}
		if (!taskAvailable) {
			System.out.println("SUBSCRIBING to queue [" + this.queue.getName() + "]");
			for (QueueListener l : this.listeners.values()) {
				this.queue.addItemListener(l, true);
			}
			// this.subscribed.set(true);
		}
	}*/

	/*private synchronized void resumeUnorderedTasks() {
		ProjectTask task = queue.poll();
		if (task != null) {
			runTask(task);
		} else {
			System.out.println("SUBSCRIBING to queue [" + this.queue.getName() + "]");
			for (QueueListener l : this.listeners.values()) {
				this.queue.addItemListener(l, true);
			}
			// this.subscribed.set(true);
		}
	}*/
}