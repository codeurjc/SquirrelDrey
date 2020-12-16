package es.codeurjc.squirrel.drey.local;

import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import es.codeurjc.squirrel.drey.local.autoscaling.InfrastructureManager;
import es.codeurjc.squirrel.drey.local.utils.EC2Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.codeurjc.squirrel.drey.local.Algorithm.Status;

/**
 * 
 * @author Iván Chicano (ivchicano.urjc@gmail.com)
 */
public class AlgorithmManager<R extends Serializable> {

	private static final Logger log = LoggerFactory.getLogger(AlgorithmManager.class);

	private boolean mastermode = false;
	private boolean devmode = false;
	private boolean autoscaling = false;
	SQSConnectorMaster<R> sqsMaster;
	SQSConnectorWorker<R> sqsWorker;

	// Algorithm callbacks for master
	Map<String, Consumer<R>> algorithmCallbacksConsumers;
	Map<String, AlgorithmCallback<R>> algorithmCallbacks;

	Map<String, AlgorithmInfo> algorithmInfo;

	// Infrastructure attributes
	ReentrantLock sharedInfrastructureManagerLock;
	InfrastructureManager<R> infrastructureManager;
	String workerId;
	String ec2InstanceId;
	long launchingTime;
	long lastTimeWorking;
	WorkerStatus workerStatus;

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
	CountDownLatch algInfoFetched;

	public AlgorithmManager(Object... args) {
		this.devmode = System.getProperty("devmode") == null || Boolean.parseBoolean(System.getProperty("devmode"));
		this.autoscaling = System.getProperty("enable-autoscaling") != null || Boolean.parseBoolean(System.getProperty("enable-autoscaling"));
		this.launchingTime = System.currentTimeMillis();
		this.workerStatsFetched = new CountDownLatch(0);
		if (this.devmode) {
			log.info("Devmode enabled");
			this.workerId = UUID.randomUUID().toString();
			this.launchingTime = System.currentTimeMillis();
			this.workerStatus = WorkerStatus.running;
			this.initializeWorker();
		} else {
			this.mastermode = System.getProperty("worker") != null && !Boolean.parseBoolean(System.getProperty("worker"));
			if (this.mastermode) {
				log.info("Starting as MASTER");
				log.info("Initializing...");
				this.initializeMaster();
			} else {
				log.info("Starting as WORKER");
				log.info("Initializing...");
				this.workerId = UUID.randomUUID().toString();
				this.workerStatus = WorkerStatus.running;

				// If autoscaling is enabled, the instance is in AWS
				if (autoscaling) {
					try {
						this.ec2InstanceId = EC2Utils.retrieveInstanceId();
					} catch (Exception e) {
						log.error("Error getting ec2 instance id: {}", e.getMessage());
					}
				}
				this.sqsWorker = new SQSConnectorWorker<>(this.workerId, this);
				this.initializeWorker();
				try {
					this.sqsWorker.establishDirectConnection(false);
				} catch (Exception e) {
					log.error(e.getMessage());
					e.printStackTrace();
				}
			}
		}
		printStartingInfo();
	}

	private void initializeMaster() {
		this.algorithms = new ConcurrentHashMap<>();
		this.algorithmCallbacksConsumers = new ConcurrentHashMap<>();
		this.algorithmCallbacks = new ConcurrentHashMap<>();
		this.terminateOneBlockingLatches = new ConcurrentHashMap<>();
		this.algorithmInfo = new ConcurrentHashMap<>();
		this.sharedInfrastructureManagerLock = new ReentrantLock();
		this.infrastructureManager = new InfrastructureManager<R>(this, sharedInfrastructureManagerLock);
		this.sqsMaster = new SQSConnectorMaster<R>(this, sharedInfrastructureManagerLock);
	}

	private void initializeWorker() {
		Mode mode = System.getProperty("mode") != null ? Mode.valueOf(System.getProperty("mode")) : Mode.RANDOM;

		this.algorithms = new ConcurrentHashMap<>();
		this.taskCompletedEventsCount = new ConcurrentHashMap<>();
		this.taskCompletedLocks = new ConcurrentHashMap<>();
		this.taskTimeoutLocks = new ConcurrentHashMap<>();
		this.algorithmStructures = new ConcurrentHashMap<>();
		this.algorithmsMarkedWithTimeout = new ConcurrentHashMap<>();
		this.algorithmsRunningAndFinishedTasksOnTimeout = new ConcurrentHashMap<>();

		this.QUEUES = new ConcurrentHashMap<>();

		this.algorithmQueues = new ConcurrentHashMap<>();
		this.algorithmAddedTasks = new ConcurrentHashMap<>();
		this.algorithmCompletedTasks = new ConcurrentHashMap<>();
		this.algorithmTimeoutTasks = new ConcurrentHashMap<>();

		this.queuesManager = new QueuesManager<R>(this, mode);

		final int totalNumberOfCores = Runtime.getRuntime().availableProcessors();
		log.info("Total number of cores: {}", totalNumberOfCores);

		int idleCores;
		String idlesCoresApp = System.getProperty("idle-cores-app");
		if (idlesCoresApp != null) {
			idleCores = Integer.parseInt(idlesCoresApp);
		} else if (this.devmode) {
			idleCores = 0;
		} else {
			idleCores = 1; // 1 idle core for comunications with SQS
		}

		log.info("Application worker will have {} idle cores", idleCores);

		this.queuesManager.initialize(idleCores);
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority) throws Exception {
		this.solveAlgorithm(id, initialTask, priority, false);
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority, boolean isLowPriority) throws Exception {
		Algorithm<R> alg = new Algorithm<R>(this, id, priority, initialTask);
		if (this.mastermode) {
			if (this.algorithms.containsKey(id)) {
				throw new Exception("Algorithm with id [" + id + "] already exists");
			}
			this.algorithms.putIfAbsent(id, alg);
			log.info("Is low priority: {}", isLowPriority);
			this.sqsMaster.sendAlgorithm(alg, isLowPriority);
		} else {
			this.solveAlgorithmAux(id, alg);
		}
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority, Consumer<R> callback) throws Exception {
		this.solveAlgorithm(id, initialTask, priority, callback, false);
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority, Consumer<R> callback,
			boolean isLowPriority) throws Exception {
		Algorithm<R> alg = new Algorithm<R>(this, id, priority, initialTask, callback);
		if (this.mastermode) {
			if (this.algorithms.containsKey(id)) {
				throw new Exception("Algorithm with id [" + id + "] already exists");
			}
			this.algorithms.putIfAbsent(id, alg);
			this.algorithmCallbacksConsumers.put(id, callback);
			log.info("Is low priority: {}", isLowPriority);
			this.sqsMaster.sendAlgorithm(alg, isLowPriority);
		} else {
			this.solveAlgorithmAux(id, alg);
		}
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority, AlgorithmCallback<R> callback)
			throws Exception {
		this.solveAlgorithm(id, initialTask, priority, callback, false);
	}

	public void solveAlgorithm(String id, Task initialTask, Integer priority, AlgorithmCallback<R> callback,
			boolean isLowPriority) throws Exception {
		Algorithm<R> alg = new Algorithm<R>(this, id, priority, initialTask, callback);
		if (this.mastermode) {
			if (this.algorithms.containsKey(id)) {
				throw new Exception("Algorithm with id [" + id + "] already exists");
			}
			this.algorithms.putIfAbsent(id, alg);
			this.algorithmCallbacks.put(id, callback);
			log.info("Is low priority: {}", isLowPriority);
			this.sqsMaster.sendAlgorithm(alg, isLowPriority);
		} else {
			this.solveAlgorithmAux(id, alg);
		}
	}

	void solveAlgorithmAux(String id, Algorithm<R> alg) throws Exception {
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

		if (alg.getAlgorithmManager() == null) {
			alg.setAlgorithmManager(this);
		}

		alg.solve(queue);
	}

	public void taskCompleted(AlgorithmEvent ev) throws InterruptedException, IOException {
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
						if (this.devmode) {
							alg.runCallbackSuccess();
						} else {
							alg.markCompleted();
							this.sqsWorker.sendResult(alg);
						}
					} catch (Exception e) {
						log.error(e.getMessage());
						e.printStackTrace();
					}

					this.cleanAlgorithmStructures(algId);
				} else if (this.algorithmsMarkedWithTimeout.get(algId)) {
					log.warn("ALGORITHM TIMEOUT: Algorithm: {}, Completed task: {}", algId, t);
					final Integer runningAndFinishedTasks = this.algorithmsRunningAndFinishedTasksOnTimeout.get(algId);
					if (alg.hasFinishedRunningTasks(runningAndFinishedTasks)) {
						try {
							log.warn("Last running task [{}] in algorithm [{}]. Starting algorithm termination", t,
									algId);
							Algorithm<R> terminatedAlg = this.terminateOneAlgorithmBlockingWorker(algId);
							if (!this.devmode) {
								this.sqsWorker.sendTerminateOneAlgorithmBlocking(terminatedAlg);
							}
						} catch (Exception e) {
							log.error(
									"Error while forcibly terminating algorithm [{}] for task [{}] triggering timeout: {}",
									algId, t, e.getMessage());
						}
						if (this.devmode) {
							alg.runCallbackError(Status.TIMEOUT);
						} else {
							this.sqsWorker.sendError(alg, Status.TIMEOUT);
						}
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

	public void taskTimeout(AlgorithmEvent ev) throws InterruptedException, IOException {
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
						Algorithm<R> terminatedAlg = this.terminateOneAlgorithmBlockingWorker(algId);
						if (!this.devmode) {
							this.sqsWorker.sendTerminateOneAlgorithmBlocking(terminatedAlg);
						}
					} catch (Exception e) {
						log.error(
								"Error while forcibly terminating algorithm [{}] for task [{}] triggering timeout: {}",
								algId, t, e.getMessage());
					}
					if (this.devmode) {
						alg.runCallbackError(Status.TIMEOUT);
					} else {
						this.sqsWorker.sendError(alg, Status.TIMEOUT);
					}
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
		if (this.mastermode) {
			return cleanAlgorithmStructuresMaster(algorithmId);
		} else {
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
	}

	private Algorithm<R> cleanAlgorithmStructuresMaster(String algorithmId) {
		// Remove algorithm
		Algorithm<R> alg = this.algorithms.remove(algorithmId);
		this.algorithmCallbacksConsumers.remove(algorithmId);
		this.algorithmCallbacks.remove(algorithmId);
		this.algorithmInfo.remove(algorithmId);
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

	public void runCallback(Algorithm<R> algorithm) throws Exception {
		this.algorithms.put(algorithm.getId(), algorithm);
		Consumer<R> callback = this.algorithmCallbacksConsumers.get(algorithm.getId());
		if (callback != null) {
			algorithm.setCallback(callback);
			algorithm.runCallbackSuccess();
		} else {
			AlgorithmCallback<R> algorithmCallback = this.algorithmCallbacks.get(algorithm.getId());
			if (algorithmCallback != null) {
				algorithm.setAlgorithmCallback(algorithmCallback);
			}
			algorithm.runCallbackSuccess();
		}
		this.cleanAlgorithmStructuresMaster(algorithm.getId());
	}

	public void runCallbackError(Algorithm<R> algorithm, Status errorStatus) throws Exception {
		this.algorithms.put(algorithm.getId(), algorithm);
		AlgorithmCallback<R> algorithmCallback = this.algorithmCallbacks.get(algorithm.getId());
		if (algorithmCallback != null) {
			algorithm.setAlgorithmCallback(algorithmCallback);
		}
		algorithm.runCallbackError(errorStatus);
		this.cleanAlgorithmStructuresMaster(algorithm.getId());
	}

	public Algorithm<R> getAlgorithm(String algorithmId) {
		return this.algorithms.get(algorithmId);
	}

	public Collection<Algorithm<R>> getAllAlgorithms() {
		return this.algorithms.values();
	}

	public Map<String, WorkerStats> fetchWorkers(int maxSecondsToWait) throws TimeoutException, IOException {
		return this.infrastructureManager.getWorkers();
	}

	public Map<String, WorkerStats> fetchInfrastructureWorkers(int maxSecondsToWait) throws TimeoutException, IOException {
		if (this.devmode) {
			WorkerStats stats = this.getWorkerStats();
			Map<String, WorkerStats> statsMap = new HashMap<>();
			statsMap.put(this.workerId, stats);
			log.info("Sending worker stats map (devmode): {}", statsMap);
			return statsMap;
		} else {
			// We get the current number of workers as countdown measure
			// Other workers could join during the process
			final int NUMBER_OF_WORKERS = this.sqsMaster.getNumberOfWorkers();
			this.workerStatsFetched = new CountDownLatch(NUMBER_OF_WORKERS);

			this.sqsMaster.fetchWorkerStats();

			try {
				if (this.workerStatsFetched.await(maxSecondsToWait, TimeUnit.SECONDS)) {
					return this.infrastructureManager.getWorkers();

				} else {
					log.error("Timeout ({} s) while waiting for all {} workers to update their stats", maxSecondsToWait,
							NUMBER_OF_WORKERS);
					throw new TimeoutException("Timeout of " + maxSecondsToWait + " elapsed");
				}
			} catch (InterruptedException e) {
				log.error("Error while waiting for workers to update their stats: {}", e.getMessage());
				return null;
			}
		}
	}

	WorkerStats getWorkerStats() {
		return this.queuesManager.fetchWorkerStats();
	}

	List<AlgorithmInfo> getAlgorithmInfoWorker() {
		return this.algorithms.values().stream()
				.map(algorithm -> new AlgorithmInfo(algorithm.getId(), algorithm.getTasksAdded(),
						algorithm.getTasksCompleted(), algorithm.getTasksQueued(), algorithm.getTasksTimeout(),
						algorithm.getTimeOfProcessing(), algorithm.getPriority()))
				.collect(Collectors.toList());
	}

	WorkerStats workerStatsReceivedFromAutodiscovery(String id, WorkerStats workerStats) {
		try {
			this.sharedInfrastructureManagerLock.lock();
			return this.infrastructureManager.getWorkers().put(id, workerStats);
		} finally {
			this.sharedInfrastructureManagerLock.unlock();
		}
	}

	void workerStatsReceived(String id, WorkerStats workerStats) {
		try {
			this.sharedInfrastructureManagerLock.lock();
			this.infrastructureManager.getWorkers().put(id, workerStats);
			this.workerStatsFetched.countDown();
		} finally {
			this.sharedInfrastructureManagerLock.unlock();
		}
	}

	WorkerStats workerStats(WorkerEvent ev) {
		log.debug("WORKER EVENT for worker [{}]: {}", ev.getWorkerId(), ev.getContent());
		return (WorkerStats) ev.getContent();
	}

	public List<Algorithm<R>> terminateAlgorithms() throws IOException {
		this.sqsMaster.terminateAlgorithms();
		return this.clearAllAlgorithmsFromTermination();
	}

	public List<Algorithm<R>> blockingTerminateAlgorithms() throws InterruptedException, IOException {
		// We get the current number of workers as countdown measure
		// Other workers could join during the process
		final int NUMBER_OF_WORKERS = this.sqsMaster.getNumberOfWorkers();
		this.terminateBlockingLatch = new CountDownLatch(NUMBER_OF_WORKERS);
		timeForTerminate = System.currentTimeMillis();

		this.sqsMaster.terminateAlgorithmsBlocking();

		this.terminateBlockingLatch.await(20, TimeUnit.SECONDS);
		return this.clearAllAlgorithmsFromTermination();
	}

	public Algorithm<R> blockingTerminateOneAlgorithm(String algorithmId) throws InterruptedException, IOException {
		this.terminateOneBlockingLatches.put(algorithmId, new CountDownLatch(1));
		this.sqsMaster.stopOneAlgorithmBlocking(algorithmId);
		this.terminateOneBlockingLatches.get(algorithmId).await(20, TimeUnit.SECONDS);
		Algorithm<R> alg = this.cleanAlgorithmStructures(algorithmId);
		if (alg != null) {
			alg.setStatus(Status.TERMINATED);
		}
		return alg;
	}

	List<Algorithm<R>> terminateAlgorithmsWorker() {
		this.queuesManager.terminateAlgorithmsNotBlocking();
		return this.clearAllAlgorithmsFromTermination();
	}

	private List<Algorithm<R>> clearAllAlgorithmsFromTermination() {
		List<Algorithm<R>> algs = new ArrayList<>();
		Algorithm<R> alg;
		for (String algorithmId : this.algorithms.keySet()) {
			alg = this.cleanAlgorithmStructures(algorithmId);
			alg.setStatus(Status.TERMINATED);
			if (alg != null) {
				algs.add(alg);
			}
		}
		return algs;
	}

	List<Algorithm<R>> terminateAlgorithmsBlockingWorker() {
		this.queuesManager.terminateAlgorithmsBlocking();
		return this.clearAllAlgorithmsFromTermination();
	}

	public Algorithm<R> terminateOneAlgorithmBlockingWorker(String algorithmId) {
		if (this.algorithms.get(algorithmId) != null) {
			this.queuesManager.terminateOneAlgorithmBlocking(algorithmId);
			Algorithm<R> alg = this.cleanAlgorithmStructures(algorithmId);
			if (alg != null) {
				alg.setStatus(Status.TERMINATED);
			}
			return alg;
		} else {
			return null;
		}
	}

	public SQSConnectorMaster<R> getSqsMaster() {
		return sqsMaster;
	}

	public void deleteDirectQueues() {
		this.sqsMaster.deleteDirectQueues();
	}

	public void deleteInputQueue() {
		this.sqsMaster.deleteInputQueues();
	}

	public void deleteOutputQueue() {
		this.sqsMaster.deleteOutputQueues();
	}

	public void deleteQueues() {
		this.deleteDirectQueues();
		this.deleteInputQueue();
		this.deleteOutputQueue();
	}

	public Map<String, AlgorithmInfo> getAlgorithmInfo(int maxSecondsToWait) throws IOException, TimeoutException {
		if (this.devmode) {
			List<AlgorithmInfo> algorithmInfo = this.getAlgorithmInfoWorker();
			Map<String, AlgorithmInfo> algInfoMap = new ConcurrentHashMap<>();
			for (AlgorithmInfo info : algorithmInfo) {
				algInfoMap.put(info.getAlgorithmId(), info);
			}
			return algInfoMap;
		} else {
			// We get the current number of workers as countdown measure
			// Other workers could join during the process
			final int NUMBER_OF_WORKERS = this.sqsMaster.getNumberOfWorkers();
			this.algInfoFetched = new CountDownLatch(NUMBER_OF_WORKERS);
			this.sqsMaster.fetchAlgorithmInfo();

			try {
				if (this.algInfoFetched.await(maxSecondsToWait, TimeUnit.SECONDS)) {
					return this.algorithmInfo;
				} else {
					log.error("Timeout ({} s) while waiting for all {} workers to update their stats", maxSecondsToWait,
							NUMBER_OF_WORKERS);
					throw new TimeoutException("Timeout of " + maxSecondsToWait + " elapsed");
				}
			} catch (InterruptedException e) {
				log.error("Error while waiting for workers to update their stats: {}", e.getMessage());
				return null;
			}
		}

	}

	void algorithmInfoReceived(List<AlgorithmInfo> algorithmInfo) {
		for (AlgorithmInfo info : algorithmInfo) {
			this.algorithmInfo.put(info.getAlgorithmId(), info);
		}
		this.algInfoFetched.countDown();
	}

	private void printStartingInfo() {
		SimpleDateFormat sdf = new SimpleDateFormat();
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		String message = "\n";
		message += "===========================\n";
		message += "STARTING AS: " + (this.mastermode ? "MASTER" : "WORKER") + "\n";
		message += "DEPLOYING AS: " + ((this.devmode) ? "DEV" : "PROD") + "\n";
		message += "AUTOSCALING ENABLED: " + this.autoscaling + "\n";
		message += "EC2 Instance Id: " + ((this.ec2InstanceId != null ? this.ec2InstanceId : "none")) + "\n";
		message += "CREATION TIME TIMESTAMP: " + ((this.launchingTime != 0L ? this.launchingTime : "none")) + "\n";
		message += "CREATION TIME UTC: " + ((this.launchingTime != 0L ? sdf.format(new Date(this.launchingTime)) : "none")) + "\n";
		if (!this.mastermode) {
			message += "DIRECT QUEUE URL: " + this.sqsWorker.directQueueUrl + "\n";
			message += "WORKER ID: " + ((this.workerId != null ? this.workerId : "none")) + "\n";
			message += "WORKER STATUS: " + ((this.workerStatus != null ? this.workerStatus.name() : "none")) + "\n";
		}
		message += "===========================\n";
		log.info(message);
	}
}