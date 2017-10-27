package es.codeurjc.distributed.algorithm;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;

import es.codeurjc.sampleapp.App;

public class QueuesManager {

	HazelcastInstance hc;
	
	IMap<String, Integer> mapOfQueues; 		 	// Distributed map storing all the distributed queues and their priority
	MapOfQueuesListener mapOfQueuesListener;	// Listener for distributed map of queues
	Map<String, QueueListener> queuesListeners; // Local map storing all the listeners for all distributed queues
	ThreadPoolExecutor executor; 			 	// Local thread pool to run tasks retrieved from distributed queues

	public QueuesManager(HazelcastInstance hc) {
		this.hc = hc;

		// Initialize thread pool
		int processors = Runtime.getRuntime().availableProcessors();
		App.logger.info("Number of cores: " + processors);
		this.executor = new ThreadPoolExecutor(processors, processors, 0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>());
		Executors.newFixedThreadPool(processors);

		queuesListeners = new HashMap<>();
		mapOfQueues = hc.getMap("QUEUES");
		mapOfQueuesListener = new MapOfQueuesListener(this);

		// Add a listener to the map of queues to listen for new queues created after node is launched
		mapOfQueues.addEntryListener(mapOfQueuesListener, true);

		// Add a listener to every existing queue when the node is launched, by priority
		subscribeToQueues(mapOfQueues.keySet());

		// Start polling
		performOrderedPolling();
	}

	public void subscribeToQueues(Set<String> queueIds) {
		for (String queueId : queueIds) {
			IQueue<Task<?>> queue = this.hc.getQueue(queueId);
			addQueueListener(queue);
		}
		if (!queueIds.isEmpty()) {
			App.logger.info("SUBSCRIBING to queues " + queueIds.toString());
		}
	}

	public void unsubscribeFromQueues(Set<String> queueIds) {
		for (String queueId : queueIds) {
			IQueue<Task<?>> queue = hc.getQueue(queueId);
			QueueListener listener = queuesListeners.remove(queueId);
			queue.removeItemListener(listener.getId());
		}
		if (!queueIds.isEmpty()) {
			App.logger.info("UNSUBSCRIBING from queues " + queueIds.toString());
		}
	}

	public void addQueueListener(IQueue<Task<?>> queue) {
		QueueListener listener = new QueueListener(queue, this);
		String listenerId = queue.addItemListener(listener, true);
		listener.setId(listenerId);
		queuesListeners.put(queue.getName(), listener);
	}

	public boolean hasAvailableProcessors() {
		return executor.getActiveCount() < Runtime.getRuntime().availableProcessors();
	}
	
	public IQueue<Task<?>> getQueue(String queueId) {
		return this.hc.getQueue(queueId);
	}
	
	public void performOrderedPolling() {
		if (hasAvailableProcessors()) {
			Map<String, Integer> orderedMap = sortMapByValue(mapOfQueues);
			boolean taskAvailable = false;
			for (String queueId : orderedMap.keySet()) {
				IQueue<Task<?>> q = hc.getQueue(queueId);
				Task<?> task = q.poll();
				if (task != null) {
					runTask(task);
					taskAvailable = true;
					break;
				}
			}
			// If there's no task and there's no queue listener
			if (!taskAvailable && queuesListeners.isEmpty()) {
				subscribeToQueues(mapOfQueues.keySet());
			}
		} else {
			// If there are any listeners left, unsubscribe from them
			if (!queuesListeners.isEmpty()) {
				unsubscribeFromQueues(this.queuesListeners.keySet());
			}
		}
	}
	
	public boolean performRandomPolling() {
		return false;
	}
	
	public void runTask(Task<?> task) {
		task.setHazelcastInstance(this.hc);
		CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
			try {
				task.process();
			} catch (Exception e) {
				App.logger.error("ERROR: " + e);
			}
			return null;
		}, executor);
		future.thenAcceptAsync(result -> {
			try {
				task.callback();
				performOrderedPolling();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, executor);
	}
	
	
	
	
	public static <K, V extends Comparable<? super V>> Map<K, V> sortMapByValue(Map<K, V> map) {
		List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>(map.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
			public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
				return (o1.getValue()).compareTo(o2.getValue());
			}
		});

		Map<K, V> result = new LinkedHashMap<K, V>();
		for (Map.Entry<K, V> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

}
