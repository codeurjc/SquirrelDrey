package es.codeurjc.distributed.algorithm;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

	IMap<String, QueueProperty> mapOfQueues; 		// Distributed map storing all the distributed queues and their priority and last time updated
	MapOfQueuesListener mapOfQueuesListener; 		// Listener for distributed map of queues
	Map<String, QueueListener> queuesListeners;		// Local map storing all the listeners for all distributed queues
	ThreadPoolExecutor executor; 					// Local thread pool to run tasks retrieved from distributed queues
	
	ExecutorService executorCallbacks;
	
	AtomicBoolean isSubscribed = new AtomicBoolean(false);
	Mode mode;
	
	public QueuesManager(Mode mode) {
		this.mode = mode;
	}
	
	public void initializeHazelcast(HazelcastInstance hc) {
		this.hc = hc;

		// Initialize thread pool
		int processors = Runtime.getRuntime().availableProcessors();
		log.info("Number of cores: " + processors);
		log.info("Using " + this.mode + " task selection strategy");
		
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
			
			Map<String, Integer> orderedMap = null;
			if (mode.equals(Mode.PRIORITY)) {
				orderedMap = sortMapByPriority(mapOfQueues);
			} else if ((mode.equals(Mode.RANDOM))){
				orderedMap = sortMapByWeightedRandom(mapOfQueues);
			}

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
				log.info("Task [{}] submitted for algorithm [{}]", task, task.algorithmId);
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
				log.info("Starting task [{}] for algorithm [{}]", task, task.algorithmId);
				task.process();
			} catch (Exception e) {
				log.error("ERROR: " + e);
			}
			return null;
		}, executor);
		future.thenAcceptAsync(result -> {
			try {
				task.callback();
				log.info("Finished task [{}] for algorithm [{}] with result [{}]", task, task.algorithmId, task.getResult());
				lookQueuesForTask();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, executorCallbacks);
	}
	
	
	

	
	
	public Map<String, Integer> sortMapByPriority(Map<String, QueueProperty> map) {
		Map<String, Integer> result = new LinkedHashMap<String, Integer>();
		
		if (!map.isEmpty()) {
		
			List<Map.Entry<String, QueueProperty>> list = new LinkedList<Map.Entry<String, QueueProperty>>(map.entrySet());
			Collections.sort(list, new Comparator<Map.Entry<String, QueueProperty>>() {
				public int compare(Map.Entry<String, QueueProperty> o1, Map.Entry<String, QueueProperty> o2) {
					return (o1.getValue()).compareTo(o2.getValue());
				}
			});
	
			for (Entry<String, QueueProperty> entry : list) {
				result.put(entry.getKey(), entry.getValue().getPriority());
			}
		}
		
		return result;
	}
	
	public Map<String, Integer> sortMapByWeightedRandom(Map<String, QueueProperty> map) {
		
		Map<String, Integer> result = new LinkedHashMap<String, Integer>();
		
		if (!map.isEmpty()) {
			List<Entry<String, QueueProperty>> list = weightedSortEntries(map);
			for (Map.Entry<String, QueueProperty> entry : list) {
				result.put(entry.getKey(), entry.getValue().getLastTimeUpdated().get());
			}
		}
		
		return result;
	}
	
	public List<Entry<String, QueueProperty>> weightedSortEntries(Map<String, QueueProperty> map) {
		
		List<Entry<String, QueueProperty>> values = new LinkedList<Map.Entry<String, QueueProperty>>(map.entrySet());
		
		System.out.println(values);
		
		Map<String, Pair> labels = getLabels(values);
		
		List<Entry<String, QueueProperty>> ordered = new ArrayList<>();
		
		do {
			double random = Math.random();
			Entry<String, QueueProperty> picked = null;
						
			for (Entry<String, Pair> label : labels.entrySet()) {
				if (label.getValue().bottom <= random && label.getValue().top > random) {
					picked = new AbstractMap.SimpleEntry<>(label.getKey(), map.get(label.getKey()));
					break;
				}
			}
			
			if (ordered.size() == 0 && picked != null) log.info("FIRST QUEUE PICKED [{}]", picked.getKey());
			
			for (int i=0; i < values.size(); i++) {
				if (values.get(i).getKey().equals(picked.getKey())) {
					values.remove(i);
				}
			}
			
			labels = getLabels(values);
			ordered.add(picked);
		} while (!values.isEmpty());
		
		return ordered;
	}
	
	private Map<String, Pair> getLabels(List<Entry<String, QueueProperty>> values) {
		Map<String, Integer> weights = new HashMap<>();
		int totalWeight = 0;
		for (Entry<String, QueueProperty> entry : values) {
			// Seconds since last addition to the queue
			int t1 = (int) System.currentTimeMillis();
			int t2 = entry.getValue().getLastTimeUpdated().get();
			Integer w =  ((t1 - t2) / 1000);
			totalWeight += w;
			weights.put(entry.getKey(), w);
		}
		
		double weightUnit;
		if (totalWeight == 0) {
			weightUnit = 1.0 / values.size();
		} else {
			weightUnit = (1.0 / totalWeight);
		}
		
		double previousTop = 0.0;
		Map<String, Pair> labels = new HashMap<>();
		for (Entry<String, QueueProperty> entry : values) {
			double factor;
			if (totalWeight == 0) {
				factor = 1.0;
			} else {
				factor = weights.get(entry.getKey());
			}
			System.out.println("WEIGHTUNIT: " + weightUnit + ", FACTOR: " + factor);
			double newTop = previousTop + weightUnit * factor;
			labels.put(entry.getKey(), new Pair(previousTop, newTop));
			previousTop = newTop + 0.00000001;
		}
		System.out.println("LABELS: " + labels);
		return labels;
	}
	
	private class Pair {
		double bottom;
		double top;
		
		public Pair(double bottom, double top) {
			this.bottom = bottom;
			this.top = top;
		}
		
		@Override
		public String toString() {
			return ("(" + bottom + ", " + top + ")");
		}
	}

}
