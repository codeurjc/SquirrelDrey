package es.codeurjc.squirrel.drey;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.Member;

import es.codeurjc.squirrel.drey.Task.Status;

public class QueuesManager {

	private static final Logger log = LoggerFactory.getLogger(QueuesManager.class);

	HazelcastInstance hc;

	/* Distributed structures */
	IMap<String, QueueProperty> mapOfQueues; // Map storing all the distributed queues and their properties (priority,
												// last time pushed)
	IQueue<Task> maxPriorityQueue; // Queue for tasks with maximum priority. It will always be the first queue
									// checked on polling
	IMap<Integer, Task> runningTasks; // Map of running tasks in this node <taskUniqueId, task>

	/* Local structures */
	MapOfQueuesListener mapOfQueuesListener; // Listener for distributed map of queues
	Map<String, QueueListener> queuesListeners; // Map storing all the listeners for all distributed queues
	QueueListener maxPriorityQueueListener; // Listener for max priority queue

	ThreadPoolExecutor executor; // Local thread pool to run tasks retrieved from distributed queues
	ScheduledExecutorService scheduleExecutor; // Local scheduled executor for running timeout threads of tasks
	Member localMember; // Local hazelcast member
	Mode mode; // Mode of execution (RANDOM / PRIORITY)
	AtomicBoolean isSubscribed = new AtomicBoolean(false); // true if worker is subscribed to at least one queue
	int nThreads;

	CloudWatchModule cloudWatchModule;

	public QueuesManager(Mode mode) {
		this.mode = mode;
	}

	public void initializeHazelcast(HazelcastInstance hc, int numberOfIdleCores) {

		this.hc = hc;

		// Initialize thread pool. Number of processors minus the number of idle cores
		// specified so worker communications are never blocked
		this.nThreads = Runtime.getRuntime().availableProcessors() - numberOfIdleCores;
		log.info("Number of working cores to run tasks: " + nThreads);
		log.info("Using " + this.mode + " task selection strategy");

		if (nThreads > 0) {
			this.executor = new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
					new LinkedBlockingQueue<Runnable>());
			this.scheduleExecutor = Executors.newScheduledThreadPool(nThreads);
		}

		queuesListeners = new ConcurrentHashMap<>();
		localMember = this.hc.getCluster().getLocalMember();

		mapOfQueues = hc.getMap("QUEUES");
		maxPriorityQueue = hc.getQueue("MAX_PRIORITY_QUEUE");
		maxPriorityQueueListener = new QueueListener(maxPriorityQueue, this);
		maxPriorityQueue.addItemListener(maxPriorityQueueListener, true);

		runningTasks = hc.getMap("RUNNING_TASKS_" + localMember.getAddress().toString());

		try {
			int minutes = System.getProperty("init-timeout") != null
					? Integer.parseInt(System.getProperty("init-timeout"))
					: 3;
			if (hc.getCPSubsystem().getCPSubsystemManagementService().awaitUntilDiscoveryCompleted(minutes,
					TimeUnit.MINUTES)) {
				System.out.println(hc.getCluster().getLocalMember() + " initialized the CP subsystem with identity: "
						+ hc.getCPSubsystem().getLocalCPMember());
			} else {
				System.err.println(hc.getCluster().getLocalMember() + " couldn't initialized the CP subsystem. ");
				System.exit(1);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		log.info("Queues on startup {}", mapOfQueues.keySet().toString());

		// Prepare an active stop of algorithms
		hc.getTopic("stop-algorithms").addMessageListener((message) -> {
			this.terminateAlgorithmsNotBlocking();
		});

		// Prepare an active stop of algorithms
		hc.getTopic("stop-algorithms-blocking").addMessageListener((message) -> {
			this.terminateAlgorithmsBlocking();
		});

		hc.getTopic("stop-one-algorithm-blocking").addMessageListener((message) -> {
			this.terminateOneAlgorithmBlocking((String) message.getMessageObject());
		});

		hc.getTopic("fetch-worker-stats").addMessageListener((message) -> {
			this.publishWorkerStats(true);
		});

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
			IQueue<Task> queue = this.hc.getQueue(queueId);
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
			IQueue<Task> queue = hc.getQueue(queueId);
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
		if (nThreads == 0) {
			return false;
		} else {
			return executor.getActiveCount() < nThreads;
		}
	}

	public IQueue<Task> getQueue(String queueId) {
		return this.hc.getQueue(queueId);
	}

	public synchronized void lookQueuesForTask() {

		boolean taskAvailable = true;

		while (hasAvailableProcessors()) {

			log.info("This node can execute more tasks (Has {} task executing of {} max tasks)",
					executor.getActiveCount(), nThreads);

			Map<String, Integer> orderedMap = null;
			if (mode.equals(Mode.PRIORITY)) {
				log.info("Sorting by priority: {}", mapOfQueues.keySet().toString());
				orderedMap = sortMapByPriority(mapOfQueues);
			} else if ((mode.equals(Mode.RANDOM))) {
				log.info("Sorting by random: {}", mapOfQueues.keySet().toString());
				orderedMap = sortMapByWeightedRandom(mapOfQueues);
			}

			log.info("ORDERED MAP: {}", orderedMap.keySet().toString());

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
			if (nThreads == 0) {
				log.info("This node can NOT execute tasks because it was initialized with 0 working cores");
			} else {
				log.info("This node can NOT execute more tasks (Has {} task executing of {} max tasks)",
						executor.getActiveCount(), nThreads);

				// If there's no space available, unsubscribe from all queues
				if (this.isSubscribed.compareAndSet(true, true)) {
					unsubscribeFromQueues(this.queuesListeners.keySet());
				}
			}
		} else {
			log.info("There are no tasks in queues");
		}
	}

	private boolean submitTaskIfAvailable(Map<String, ?> orderedMap) {
		boolean taskAvailable = false;
		boolean hasNext = true;
		String queueId = "MAX_PRIORITY_QUEUE";
		Iterator<String> iterator = orderedMap.keySet().iterator();
		do {
			log.info("Trying search on queue [{}]", queueId);
			IQueue<Task> q = hc.getQueue(queueId);
			Task task = q.poll();
			if (task != null) {
				runTask(task);
				log.info("Task [{}] submitted for algorithm [{}] from queue [{}]", task, task.algorithmId, queueId);
				taskAvailable = true;
			}
			hasNext = iterator.hasNext();
			if (hasNext)
				queueId = iterator.next();
			log.info("New iterator [{}]", queueId);
		} while (hasNext && !taskAvailable);

		this.publishWorkerStats(false);
		return taskAvailable;
	}

	public boolean performRandomPolling() {
		return false;
	}

	public void runTask(Task task) {
		task.setHazelcastInstance(this.hc);

		AtomicBoolean taskTimeout = new AtomicBoolean(false);
		final Future<?>[] futures = new Future<?>[2];

		if (task.getMaxDuration() != 0) {
			futures[1] = scheduleExecutor.schedule(() -> {
				taskTimeout.getAndSet(true);
				futures[0].cancel(true);
				log.info(
						"Scheduled termination task triggered for task [{}] of algorithm [{}] due to timeout of {} ms passed",
						task, task.algorithmId, task.getMaxDuration());
				task.status = Status.TIMEOUT;
				this.hc.getTopic("task-timeout").publish(new AlgorithmEvent(task.algorithmId, "task-timeout", task));
			}, task.getMaxDuration(), TimeUnit.MILLISECONDS);
		}

		futures[0] = executor.submit(() -> {
			log.info("Starting task [{}] for algorithm [{}]", task, task.algorithmId);

			// Add task to distributed map of running tasks
			runningTasks.put(task.getId(), task);
			log.info("XXX1 " + runningTasks.size());

			task.initializeTask();
			try {
				task.process();
			} catch (Exception e) {
				log.error("Exception {} in task", e);
				return null;
			}

			if (futures[1] != null) {
				futures[1].cancel(true);
			}

			if (taskTimeout.get()) {
				log.error("Task {} TIMEOUT", task);
				return null;
			} else {
				log.info("Task {} COMPLETED", task);
			}

			try {
				task.callback();

				// Remove task from distributed map of running tasks
				runningTasks.remove(task.getId());
				log.info("XXX2 " + runningTasks.size());

				log.info("Finished task [{}] for algorithm [{}]", task, task.algorithmId);
				lookQueuesForTask();
			} catch (Exception e) {
				e.printStackTrace();
			}

			return null;
		});
	}

	private void publishWorkerStats(boolean fetched) {
		if (this.executor != null) {
			this.hc.getTopic("worker-stats").publish(new WorkerEvent(this.localMember.getAddress().toString(),
					"worker-stats", new WorkerStats(this.localMember.getAddress().toString(), this.nThreads,
							executor.getActiveCount(), executor.getTaskCount(), executor.getCompletedTaskCount()),
					fetched));
		}
	}

	public Map<String, Integer> sortMapByPriority(Map<String, QueueProperty> map) {
		Map<String, Integer> result = new LinkedHashMap<String, Integer>();

		if (!map.isEmpty()) {

			List<Map.Entry<String, QueueProperty>> list = new LinkedList<Map.Entry<String, QueueProperty>>(
					map.entrySet());
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
				result.put(entry.getKey(), 1);
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

			if (ordered.size() == 0 && picked != null)
				log.info("FIRST QUEUE PICKED [{}]", picked.getKey());

			for (int i = 0; i < values.size(); i++) {
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
			long t1 = System.currentTimeMillis();
			long t2 = entry.getValue().getLastTimeUpdated().get();
			Integer w = (Math.toIntExact(((t1 - t2) / 1000)));
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

	private void terminateAlgorithmsNotBlocking() {
		log.info("STOPPING ALL ALGORITHMS...");

		this.unsubscribeFromQueues(mapOfQueues.keySet());

		for (String queueId : mapOfQueues.keySet()) {
			IQueue<Task> queue = hc.getQueue(queueId);
			queue.destroy();
		}

		queuesListeners.clear();
		runningTasks.clear();
		mapOfQueues.clear();
		maxPriorityQueue.clear();

		executor.shutdown();
		scheduleExecutor.shutdown();

		if (nThreads > 0) {
			this.executor = new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
					new LinkedBlockingQueue<Runnable>());
			this.scheduleExecutor = Executors.newScheduledThreadPool(nThreads);
		}

		this.publishWorkerStats(false);

		hc.getTopic("stop-algorithms-done").publish("");
	}

	private void terminateAlgorithmsBlocking() {
		log.info("STOPPING ALL ALGORITHMS...");

		this.unsubscribeFromQueues(mapOfQueues.keySet());

		// Clear all algorithms queues
		for (String queueId : mapOfQueues.keySet()) {
			IQueue<Task> queue = hc.getQueue(queueId);
			queue.clear();
		}

		// Destroy all algorithm queues
		for (String queueId : mapOfQueues.keySet()) {
			IQueue<Task> queue = hc.getQueue(queueId);
			queue.destroy();
		}

		queuesListeners.clear();
		runningTasks.clear();
		mapOfQueues.clear();
		maxPriorityQueue.clear();

		executor.shutdown();
		scheduleExecutor.shutdown();

		// Active wait for all data structures to be empty
		boolean allEmpty = false;
		while (!allEmpty) {
			allEmpty = queuesListeners.isEmpty() && runningTasks.isEmpty() && mapOfQueues.isEmpty()
					&& maxPriorityQueue.isEmpty();
			if (allEmpty)
				break;
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		try {
			executor.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			log.warn("ThreadPoolExecutor for tasks couldn't be gracefully shutdown. Forcing with shutdownNow");
			executor.shutdownNow();
		}

		try {
			scheduleExecutor.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			log.warn(
					"ScheduledExecutorService for timeout threads couldn't be gracefully shutdown. Forcing with shutdownNow");
			scheduleExecutor.shutdownNow();
		}

		if (nThreads > 0) {
			this.executor = new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
					new LinkedBlockingQueue<Runnable>());
			this.scheduleExecutor = Executors.newScheduledThreadPool(nThreads);
		}

		this.publishWorkerStats(false);

		hc.getTopic("stop-algorithms-done").publish("");

		log.info("GRACEFULLY TERMINATED ALL ALGORITHMS");
	}

	public void terminateOneAlgorithmBlocking(String algorithmId) {
		log.info("STOPPING ALGORITHM [{}]...", algorithmId);

		this.unsubscribeFromQueues(new HashSet<>(Arrays.asList(algorithmId)));
		IQueue<Task> queue = hc.getQueue(algorithmId);

		// Clear algorithm queue
		queue.clear();

		// Destroy algorithm queue
		queue.destroy();

		this.mapOfQueues.remove(algorithmId);

		this.publishWorkerStats(false);

		hc.getTopic("stop-one-algorithm-done").publish(algorithmId);

		log.info("GRACEFULLY TERMINATED ALGORITHM [{}]", algorithmId);
	}

}
