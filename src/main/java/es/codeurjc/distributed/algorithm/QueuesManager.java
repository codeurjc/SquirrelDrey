package es.codeurjc.distributed.algorithm;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;

public class QueuesManager {

	private static final Logger log = LoggerFactory.getLogger(QueuesManager.class);

	HazelcastInstance hc;

	IMap<String, Integer> mapOfQueues; 			// Distributed map storing all the distributed queues and their priority
	MapOfQueuesListener mapOfQueuesListener; 	// Listener for distributed map of queues
	Map<String, QueueListener> queuesListeners;	// Local map storing all the listeners for all distributed queues
	ThreadPoolExecutor executor; 				// Local thread pool to run tasks retrieved from distributed queues
	
	ExecutorService executorCallbacks;
	
	AtomicBoolean isSubscribed = new AtomicBoolean(false);
	
	public QueuesManager() {}
	
	public void initializeHazelcast(HazelcastInstance hc) {
		this.hc = hc;

		// Initialize thread pool
		int processors = Runtime.getRuntime().availableProcessors();
		log.info("Number of cores: " + processors);
		
		this.executor = new ThreadPoolExecutor(processors, processors, 0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>());
		
		this.executorCallbacks = Executors.newSingleThreadExecutor();
		
		Executors.newFixedThreadPool(processors);

		queuesListeners = new ConcurrentHashMap<>();
		mapOfQueues = hc.getMap("QUEUES");
		
		log.info("Queues on startup {}", mapOfQueues.keySet().toString());

		// Start looking for tasks
		lookQueuesForTask();
	}

	public synchronized void subscribeToQueues(Set<String> queueIds) {
		log.info("Trying to subscribe to {}", queueIds);
		Set<String> s = new HashSet<>();
		for (String queueId : queueIds) {
			if (this.queuesListeners.containsKey(queueId)) {
				 log.info("(Already subscribed to [{}])", queueId);
				 continue;
			}
			IQueue<Task<?>> queue = this.hc.getQueue(queueId);
			QueueListener listener = new QueueListener(queue, this);
			String listenerId = queue.addItemListener(listener, true);
			listener.setId(listenerId);
			queuesListeners.put(queueId, listener);
			s.add(queueId);
		}
		if (!s.isEmpty() && !this.queuesListeners.isEmpty()) {
			this.isSubscribed.set(true);
			log.info("SUBSCRIBED to queues " + s.toString());
		}
	}

	public synchronized void unsubscribeFromQueues(Set<String> queueIds) {
		log.info("Trying to unsubscribe from {}", queueIds);
		Set<String> desiredUnsubs = new HashSet<>(queueIds);
		Set<String> performedUnsubs = new HashSet<>();
		for (String queueId : desiredUnsubs) {
			IQueue<Task<?>> queue = hc.getQueue(queueId);
			QueueListener listener = queuesListeners.remove(queueId);

			if (listener != null) {
				queue.removeItemListener(listener.getId());
				performedUnsubs.add(queueId);
			} else {
				log.info("(Already unsubscribed from [{}])", queueId);
			}
		}
		if (!performedUnsubs.isEmpty() && this.queuesListeners.isEmpty()) {
			this.isSubscribed.set(false);
			log.info("UNSUBSCRIBED from queues " + performedUnsubs.toString());
		}
	}

	public boolean hasAvailableProcessors() {
		return executor.getActiveCount() < Runtime.getRuntime().availableProcessors();
	}

	public IQueue<Task<?>> getQueue(String queueId) {
		return this.hc.getQueue(queueId);
	}

	
	
	
	public synchronized void lookQueuesForTask() {

		boolean taskAvailable = true;

		while (hasAvailableProcessors()) {

			log.info("This node can execute more tasks (Has {} task executing of {} max tasks)",
					executor.getActiveCount(), Runtime.getRuntime().availableProcessors());

			Map<String, Integer> orderedMap = sortMapByValue(mapOfQueues);

			taskAvailable = submitTaskIfAvailable(orderedMap);

			// If there's no task and no subscription
			if (!taskAvailable && hasAvailableProcessors()) {

				subscribeToQueues(mapOfQueues.keySet());
				taskAvailable = submitTaskIfAvailable(orderedMap);

				if (!taskAvailable) {
					break;
				}
			}
		}

		if (taskAvailable) {
			log.info("This node can NOT execute more tasks (Has {} task executing of {} max tasks)",
					executor.getActiveCount(), Runtime.getRuntime().availableProcessors());
			
			// If there's no space available, unsubscribe from all queues
			if (this.isSubscribed.compareAndSet(true, true)) {
				unsubscribeFromQueues(this.queuesListeners.keySet());
			}
		} else {
			log.info("There are no tasks in queues");
		}

	}

	private boolean submitTaskIfAvailable(Map<String, Integer> orderedMap) {
		boolean taskAvailable = false;
		for (String queueId : orderedMap.keySet()) {
			IQueue<Task<?>> q = hc.getQueue(queueId);
			Task<?> task = q.poll();
			if (task != null) {
				runTask(task);
				log.info("Task [{}] submitted for algorithm [{}]", task, task.algorithm.getId());
				taskAvailable = true;
				break;
			}
		}
		return taskAvailable;
	}

	public boolean performRandomPolling() {
		return false;
	}

	public void runTask(Task<?> task) {
		task.setHazelcastInstance(this.hc);
		CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
			try {
				log.info("Starting task [{}] for algorithm [{}]", task, task.algorithm.getId());
				task.process();
			} catch (Exception e) {
				log.error("ERROR: " + e);
			}
			return null;
		}, executor);
		future.thenAcceptAsync(result -> {
			try {
				task.callback();
				log.info("Finished task [{}] for algorithm [{}] with result [{}]", task, task.algorithm.getId(), task.getResult());
				lookQueuesForTask();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, executorCallbacks);
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
