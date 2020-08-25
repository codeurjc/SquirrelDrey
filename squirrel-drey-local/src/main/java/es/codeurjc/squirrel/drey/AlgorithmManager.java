package es.codeurjc.squirrel.drey;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.codeurjc.squirrel.drey.Algorithm.Status;

public class AlgorithmManager<R> {

	private static final Logger log = LoggerFactory.getLogger(AlgorithmManager.class);
	QueuesManager<R> queuesManager;

	Map<String, Algorithm<R>> algorithms;
	Map<String, AtomicLong> taskCompletedEventsCount;
	Map<String, ReentrantLock> taskCompletedLocks;
	Map<String, ReentrantLock> taskTimeoutLocks;
	Map<String, Map<String, String>> algorithmStructures;
	Map<String, Boolean> algorithmsMarkedWithTimeout;
	Map<String, Integer> algorithmsRunningAndFinishedTasksOnTimeout;
	Map<String, QueueProperty> QUEUES;

	Map<String, Queue<Task>> algorithmQueues;
	Map<String, AtomicLong> algorithmAddedTasks;
	Map<String, AtomicLong> algorithmCompletedTasks;
	Map<String, AtomicLong> algorithmTimeoutTasks;

	CountDownLatch terminateBlockingLatch;
	Map<String, CountDownLatch> terminateOneBlockingLatches;
	long timeForTerminate;

	CountDownLatch workerStatsFetched;

	public AlgorithmManager(Object... args) {
		Mode mode = System.getProperty("mode") != null ? Mode.valueOf(System.getProperty("mode")) : Mode.RANDOM;

		this.algorithms = new ConcurrentHashMap<>();
		this.taskCompletedEventsCount = new ConcurrentHashMap<>();
		this.taskCompletedLocks = new ConcurrentHashMap<>();
		this.taskTimeoutLocks = new ConcurrentHashMap<>();
		this.algorithmStructures = new ConcurrentHashMap<>();
		this.algorithmsMarkedWithTimeout = new ConcurrentHashMap<>();
		this.algorithmsRunningAndFinishedTasksOnTimeout = new ConcurrentHashMap<>();

		this.QUEUES = new ConcurrentHashMap<>();

		this.terminateOneBlockingLatches = new ConcurrentHashMap<>();

		this.algorithmQueues = new ConcurrentHashMap<>();
		this.algorithmAddedTasks = new ConcurrentHashMap<>();
		this.algorithmCompletedTasks = new ConcurrentHashMap<>();
		this.algorithmTimeoutTasks = new ConcurrentHashMap<>();

		this.queuesManager = new QueuesManager<R>(this, mode);

		final int totalNumberOfCores = Runtime.getRuntime().availableProcessors();
		log.info("Total number of cores: {}", totalNumberOfCores);

		int idleCores = System.getProperty("idle-cores-app") != null
				? Integer.parseInt(System.getProperty("idle-cores-app"))
				: 0;

		log.info("Application worker will have {} idle cores", idleCores);

		this.queuesManager.initialize(idleCores);
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority) throws Exception {
		Algorithm<R> alg = new Algorithm<>(this, id, priority, initialTask);
		this.solveAlgorithmAux(id, alg);
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority, Consumer<R> callback) throws Exception {
		Algorithm<R> alg = new Algorithm<>(this, id, priority, initialTask, callback);
		this.solveAlgorithmAux(id, alg);
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority, AlgorithmCallback<R> callback)
			throws Exception {
		Algorithm<R> alg = new Algorithm<R>(this, id, priority, initialTask, callback);
		this.solveAlgorithmAux(id, alg);
	}

	private void solveAlgorithmAux(String id, Algorithm<R> alg) throws Exception {
		if (this.algorithms.containsKey(id)) {
			throw new Exception("Algorithm with id [" + id + "] already exists");
		}

		this.algorithms.putIfAbsent(id, alg);

		this.taskCompletedEventsCount.putIfAbsent(id, new AtomicLong(0));
		this.taskCompletedLocks.putIfAbsent(alg.getId(), new ReentrantLock());
		this.taskTimeoutLocks.putIfAbsent(alg.getId(), new ReentrantLock());
		this.algorithmStructures.put(alg.getId(), new ConcurrentHashMap<>());
		this.algorithmsMarkedWithTimeout.put(alg.getId(), false);

		this.algorithmQueues.put(alg.getId(), new LinkedBlockingQueue<Task>());
		this.algorithmAddedTasks.put(alg.getId(), new AtomicLong(1)); // Starts at 1 so it counts the initial task
		this.algorithmCompletedTasks.put(alg.getId(), new AtomicLong());
		this.algorithmTimeoutTasks.put(alg.getId(), new AtomicLong());

		Queue<Task> queue = this.algorithmQueues.get(alg.getId());
		this.QUEUES.put(alg.getId(), new QueueProperty(alg.getPriority(), System.currentTimeMillis()));

		alg.solve(queue);
	}

	public void taskCompleted(AlgorithmEvent ev) {
		Task t = (Task) ev.getContent();
		log.info("TASK [{}] completed for algorithm [{}]. Took {} ms", t, ev.getAlgorithmId(),
				System.currentTimeMillis() - t.getTimeStarted());
		Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());

		this.algorithmCompletedTasks.get(ev.getAlgorithmId()).incrementAndGet();

		ReentrantLock l = this.taskCompletedLocks.get(ev.getAlgorithmId());
		l.lock();
		try {
			if (alg == null) {
				// Interruption of algorithm. This task is being terminated after stopped
				log.info("TASK {} COMPLETED FOR STOPPED ALGORITHM {}: ", t, ev.getAlgorithmId());
				this.cleanAlgorithmStructures(ev.getAlgorithmId());
			} else {

				final String algId = alg.getId();

				this.algorithmStructures.get(algId).putAll(t.getStructures());
				if (t.getFinalResult() != null)
					alg.setResult((R) t.getFinalResult());

				if (alg.hasSuccessullyFinished(this.taskCompletedEventsCount.get(algId).incrementAndGet())) {
					log.info("ALGORITHM SOLVED: Algorithm: {}, Result: {}, Last task: {}", algId, t.getFinalResult(),
							t);
					try {
						alg.runCallbackSuccess();
					} catch (Exception e) {
						log.error(e.getMessage());
					}

					this.cleanAlgorithmStructures(algId);
				} else if (this.algorithmsMarkedWithTimeout.get(algId)) {
					log.warn("ALGORITHM TIMEOUT: Algorithm: {}, Completed task: {}", algId, t);
					final Integer runningAndFinishedTasks = this.algorithmsRunningAndFinishedTasksOnTimeout.get(algId);
					if (alg.hasFinishedRunningTasks(runningAndFinishedTasks)) {
						try {
							log.warn("Last running task [{}] in algorithm [{}]. Starting algorithm termination", t,
									algId);
							this.blockingTerminateOneAlgorithm(algId);
						} catch (InterruptedException e) {
							log.error(
									"Error while forcibly terminating algorithm [{}] for task [{}] triggering timeout: {}",
									algId, t, e.getMessage());
						}
						alg.runCallbackError(Status.TIMEOUT);
					} else {
						log.warn(
								"There are still running tasks in algorithm [{}]. Last running task will trigger algorithm termination by timeout",
								algId);
					}
				}
			}
		} finally {
			l.unlock();
		}
	}

	public void taskTimeout(AlgorithmEvent ev) {
		Task t = (Task) ev.getContent();
		log.warn("TASK [{}] timeout ({} ms) for algorithm [{}]", t, t.getMaxDuration(), ev.getAlgorithmId());
		Algorithm<R> alg = this.algorithms.get(ev.getAlgorithmId());

		this.algorithmTimeoutTasks.get(ev.getAlgorithmId()).incrementAndGet();

		ReentrantLock l = this.taskTimeoutLocks.get(ev.getAlgorithmId());
		l.lock();
		try {

			if (alg == null) {
				// Interruption of algorithm. This task is triggering timeout after stopped
				log.info("TASK {} TRIGGERED TIMEOUT FOR STOPPED ALGORITHM {}: ", t, ev.getAlgorithmId());
				this.cleanAlgorithmStructures(ev.getAlgorithmId());
			} else {

				alg.addErrorTask(t);

				final String algId = alg.getId();

				int runningAndFinishedTasks;
				final Integer previouslyStoredTasks = this.algorithmsRunningAndFinishedTasksOnTimeout.get(algId);
				if (previouslyStoredTasks == null) {
					// First timeout task of the algorithm
					runningAndFinishedTasks = alg.getTasksAdded() - alg.getTasksQueued();
					log.info("Algorithm [{}] has {} running tasks when termination timeout caused by task [{}]", algId,
							runningAndFinishedTasks - alg.getTasksCompleted(), t);

					this.algorithmsRunningAndFinishedTasksOnTimeout.put(algId, runningAndFinishedTasks);
					Queue<Task> queue = this.algorithmQueues.get(algId);
					queue.clear();
					this.algorithmQueues.remove(algId);

					log.info(
							"Task queue for algorithm [{}] has been emptied because of timeout termination caused by task [{}]",
							algId, t);
				} else {
					// Other timeout tasks
					runningAndFinishedTasks = previouslyStoredTasks;
				}

				if (alg.hasFinishedRunningTasks(runningAndFinishedTasks)) {
					try {
						log.warn("Last running task [{}] in algorithm [{}]. Starting algorithm termination", t, algId);
						this.blockingTerminateOneAlgorithm(algId);
					} catch (InterruptedException e) {
						log.error(
								"Error while forcibly terminating algorithm [{}] for task [{}] triggering timeout: {}",
								algId, t, e.getMessage());
					}
					alg.runCallbackError(Status.TIMEOUT);
				} else {
					log.warn(
							"There are still running tasks in algorithm [{}]. Last running task will trigger algorithm termination by timeout",
							algId);
					this.algorithmsMarkedWithTimeout.put(algId, true);
				}
			}
		} finally {
			l.unlock();
		}
	}

	private Algorithm<R> cleanAlgorithmStructures(String algorithmId) {

		if (this.algorithmStructures.get(algorithmId) != null) {
			for (String structureId : this.algorithmStructures.get(algorithmId).values()) {
				TaskStructures.mapOfStructures.remove(structureId);
			}
			log.info("Destroyed {} Data Structures for algorithm {}: {}",
					this.algorithmStructures.get(algorithmId).keySet().size(), algorithmId,
					this.algorithmStructures.get(algorithmId).keySet());
		}

		// Remove algorithm
		Algorithm<R> alg = this.algorithms.remove(algorithmId);
		// Remove the count of task completed events
		this.taskCompletedEventsCount.remove(algorithmId);
		// Remove the completed locks for this algorithm
		this.taskCompletedLocks.remove(algorithmId);
		// Remove the tiemout locks for this algorithm
		this.taskTimeoutLocks.remove(algorithmId);
		// Remove algorithm structures
		this.algorithmStructures.remove(algorithmId);
		// Remove algorithm timeout marks
		this.algorithmsMarkedWithTimeout.remove(algorithmId);
		// Remove algorithm running tasks on timeout
		this.algorithmsRunningAndFinishedTasksOnTimeout.remove(algorithmId);

		this.algorithmQueues.remove(algorithmId);
		this.algorithmAddedTasks.remove(algorithmId);
		this.algorithmCompletedTasks.remove(algorithmId);
		this.algorithmTimeoutTasks.remove(algorithmId);

		this.QUEUES.remove(algorithmId);

		return alg;
	}

	public Algorithm<R> blockingTerminateOneAlgorithm(String algorithmId) throws InterruptedException {
		this.terminateOneBlockingLatches.put(algorithmId, new CountDownLatch(1));
		this.queuesManager.terminateOneAlgorithmBlocking(algorithmId);
		this.terminateOneBlockingLatches.get(algorithmId).await(12, TimeUnit.SECONDS);
		Algorithm<R> alg = this.cleanAlgorithmStructures(algorithmId);
		if (alg != null) {
			alg.setStatus(Status.TERMINATED);
		}
		return alg;
	}

	public void stopOneAlgorithmDone(String algorithmId) {
		log.info("Algorithm [{}] successfully terminated", algorithmId);
		this.terminateOneBlockingLatches.get(algorithmId).countDown();
	}

	public void stopAlgorithmsDone() {
		log.info("Algorithms successfully terminated on {} milliseconds",
				System.currentTimeMillis() - this.timeForTerminate);
		this.terminateBlockingLatch.countDown();
	}

	public void taskAdded(Task t, String queueId) {
		log.info("Item [" + t.toString() + "] added to queue [" + queueId + "]");

		this.queuesManager.lookQueuesForTask();
	}
}