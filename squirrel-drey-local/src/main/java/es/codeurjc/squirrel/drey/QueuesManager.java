package es.codeurjc.squirrel.drey;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Map.Entry;
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

public class QueuesManager<R> {

	private static final Logger log = LoggerFactory.getLogger(QueuesManager.class);

	Map<String, QueueProperty> mapOfQueues; // Map storing all the queues and their properties (priority,
											// last time pushed)
	Map<Integer, Task> runningTasks; // Map of running tasks <taskUniqueId, task>

	int nThreads;
	ThreadPoolExecutor executor; // Local thread pool to run tasks retrieved from queues
	ScheduledExecutorService scheduleExecutor; // Local scheduled executor for running timeout threads of tasks
	Mode mode; // Mode of execution (RANDOM / PRIORITY)
	AlgorithmManager<R> algManager;

	public QueuesManager(AlgorithmManager<R> algManager, Mode mode) {
		this.algManager = algManager;
		this.mode = mode;
	}

	public void initialize(int numberOfIdleCores) {

		this.nThreads = Runtime.getRuntime().availableProcessors() - numberOfIdleCores;
		log.info("Number of working cores to run tasks: " + nThreads);

		if (nThreads > 0) {
			this.executor = new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
					new LinkedBlockingQueue<Runnable>());
			this.scheduleExecutor = Executors.newScheduledThreadPool(nThreads);
		}

		this.mapOfQueues = this.algManager.QUEUES;
		this.runningTasks = new ConcurrentHashMap<>();

		log.info("Queues on startup {}", mapOfQueues.keySet().toString());
	}

	public void runTask(Task task) {
		task.setAlgorithmManager(this.algManager);

		AtomicBoolean taskTimeout = new AtomicBoolean(false);
		final Future<?>[] futures = new Future<?>[2];

		if (task.getMaxDuration() != 0) {
			futures[1] = scheduleExecutor.schedule(() -> {
				taskTimeout.getAndSet(true);
				futures[0].cancel(true);
				log.info(
						"Scheduled termination task triggered for task [{}] of algorithm [{}] due to timeout of {} ms passed",
						task, task.algorithmId, task.getMaxDuration());
				task.status = Task.Status.TIMEOUT;
				this.algManager.taskTimeout(new AlgorithmEvent(task.algorithmId, "task-timeout", task));
			}, task.getMaxDuration(), TimeUnit.MILLISECONDS);
		}

		futures[0] = executor.submit(() -> {
			log.info("Starting task [{}] for algorithm [{}]", task, task.algorithmId);

			// Add task to map of running tasks
			runningTasks.put(task.getId(), task);
			log.info("XXX1 " + runningTasks.size());

			task.initializeTask();
			try {
				task.process();
			} catch (Exception e) {
				log.error("Exception {} in task {}", e.getMessage(), task);
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

				// Remove task from map of running tasks
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

	public boolean hasAvailableProcessors() {
		if (nThreads == 0) {
			return false;
		} else {
			return executor.getActiveCount() < nThreads;
		}
	}

	public void terminateOneAlgorithmBlocking(String algorithmId) {
		log.info("STOPPING ALGORITHM [{}]...", algorithmId);

		// this.unsubscribeFromQueues(new HashSet<>(Arrays.asList(algorithmId)));
		Queue<Task> queue = this.algManager.algorithmQueues.get(algorithmId);

		// Clear algorithm queue
		queue.clear();

		// Destroy algorithm queue
		this.algManager.algorithmQueues.remove(algorithmId);

		this.mapOfQueues.remove(algorithmId);

		this.algManager.stopOneAlgorithmDone(algorithmId);

		log.info("GRACEFULLY TERMINATED ALGORITHM [{}]", algorithmId);
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

			// If there's no task
			if (!taskAvailable && hasAvailableProcessors()) {
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
			}
		} else {
			log.info("There are no tasks in queues");
		}
	}

	private boolean submitTaskIfAvailable(Map<String, ?> orderedMap) {
		boolean taskAvailable = false;
		Iterator<String> iterator = orderedMap.keySet().iterator();
		boolean hasNext = iterator.hasNext();
		String queueId;
		if (hasNext) {
			queueId = iterator.next();
			log.info("New iterator [{}]", queueId);
			while (hasNext && !taskAvailable) {
				Queue<Task> q = this.algManager.algorithmQueues.get(queueId);
				log.info("Trying search on queue [{}]", queueId);
				Task task = q.poll();
				if (task != null) {
					runTask(task);
					log.info("Task [{}] submitted for algorithm [{}] from queue [{}]", task, task.algorithmId, queueId);
					taskAvailable = true;
				}
				hasNext = iterator.hasNext();
				if (hasNext) {
					queueId = iterator.next();
					log.info("New iterator [{}]", queueId);
				}
			}
		}
		return taskAvailable;
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

		log.info(values.toString());

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
			log.info("WEIGHTUNIT: " + weightUnit + ", FACTOR: " + factor);
			double newTop = previousTop + weightUnit * factor;
			labels.put(entry.getKey(), new Pair(previousTop, newTop));
			previousTop = newTop + 0.00000001;
		}
		log.info("LABELS: " + labels);
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