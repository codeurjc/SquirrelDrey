package es.codeurjc.squirrel.drey.local;

import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.amazonaws.services.sqs.model.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.codeurjc.squirrel.drey.local.Algorithm.Status;

/**
 * Master class, listens to the output queue for results and sends tasks to the
 * input queue
 */
public class SQSConnectorMaster<R extends Serializable> extends SQSConnector<R> {

    private static final Logger log = LoggerFactory.getLogger(SQSConnectorMaster.class);
    private ScheduledExecutorService scheduleExecutor; // Local scheduled executor for running listener thread

    private ReentrantLock sharedInfrastructureManagerLock;
    private InfrastructureManager<R> infrastructureManager;
    private AlgorithmManager<R> algorithmManager;

    protected SQSConnectorMaster(Config config, AlgorithmManager<R> algorithmManager, ReentrantLock sharedInfrastructureManagerLock) {
        super(config, UUID.randomUUID().toString());
        this.algorithmManager = algorithmManager;
        this.sharedInfrastructureManagerLock = sharedInfrastructureManagerLock;
        this.infrastructureManager = this.algorithmManager.infrastructureManager;
        this.scheduleExecutor = Executors.newScheduledThreadPool(1);

        try {
            this.autodiscoverWorkers();
        } catch (IOException e) {
            log.error("Error while autodiscovering workers: {}", e.getMessage());
        }
        this.startListen();
    }

    protected void startListen() {
        this.scheduleExecutor.scheduleAtFixedRate(() -> {
            try {
                if (this.outputQueueUrl == null) {
                    this.lookForOutputQueue();
                }
            } catch (QueueDoesNotExistException e) {
                log.info("Output queue does not exist. Attempting to create output queue with name: {}",
                        this.outputQueueName);
                this.createOutputQueue();
            }
            try {
                Map<ObjectInputStream, Map<String, MessageAttributeValue>> siMap = messageListener(this.outputQueueUrl);
                for (Map.Entry<ObjectInputStream, Map<String, MessageAttributeValue>> si : siMap.entrySet()) {
                    switch (Enum.valueOf(MessageType.class, si.getValue().get("Type").getStringValue())) {
                        case RESULT: {
                            runCallback(si.getKey());
                            break;
                        }
                        case ESTABLISH_CONNECTION: {
                            establishConnection(si.getKey());
                            break;
                        }
                        case WORKER_STATS_AUTODISCOVERY:
                            receivedWorkerStatsFromAutodiscovery(si.getKey());
                            break;
                        case WORKER_STATS: {
                            receivedWorkerStats((AsyncResult<WorkerStats>) si.getKey().readObject());
                            break;
                        }
                        case TERMINATE_ALL_DONE: {
                            this.algorithmManager.stopAlgorithmsDone((AsyncResult<List<Algorithm<R>>>) si.getKey().readObject());
                            break;
                        }
                        case TERMINATE_ONE_DONE: {
                            this.algorithmManager.stopOneAlgorithmDone((String) si.getKey().readObject());
                            break;
                        }
                        case ERROR: {
                            runCallbackError(si.getKey());
                        }
                        case ALG_INFO: {
                            receivedAlgorithmInfo((AsyncResult<List<AlgorithmInfo>>) si.getKey().readObject());
                            break;
                        }
                        case INPUT_IS_DISABLED: {
                            receivedDisabledInput((String) si.getKey().readObject());
                            break;
                        }
                        default:
                            throw new Exception("Incorrect message type received in master: "
                                    + si.getValue().get("Type").getStringValue());
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }
        }, 0, this.sqsListenerPeriod, TimeUnit.MILLISECONDS);
    }

    private void autodiscoverWorkers() throws IOException {
        ListQueuesRequest listQueuesRequest = new ListQueuesRequest(this.directQueuePrefix);
        ListQueuesResult listQueuesResult = sqs.listQueues(listQueuesRequest);
        for(String directQueueUrl: listQueuesResult.getQueueUrls()) {
            this.send(directQueueUrl, MessageType.AUTODISCOVER_FROM_MASTER, MessageType.AUTODISCOVER_FROM_MASTER);
        }
    }

    private void receivedAlgorithmInfo(AsyncResult<List<AlgorithmInfo>> asyncResult) throws ClassNotFoundException, IOException {
        String operationId = asyncResult.getOperationId();
        List<AlgorithmInfo> algInfo = asyncResult.getResult();
        this.algorithmManager.algorithmInfoReceived(operationId, algInfo);
    }

    private void receivedDisabledInput(String operationId) {
        this.algorithmManager.receivedDisabledInput(operationId);
    }

    private void runCallbackError(ObjectInputStream si) throws Exception {
        Map<Algorithm<R>, Status> erroredAlgMap = (HashMap<Algorithm<R>, Status>) si.readObject();
        Map.Entry<Algorithm<R>, Status> erroredAlgEntry = erroredAlgMap.entrySet().iterator().next();
        this.algorithmManager.runCallbackError(erroredAlgEntry.getKey(), erroredAlgEntry.getValue());
    }

    private void receivedWorkerStatsFromAutodiscovery(ObjectInputStream si) throws ClassNotFoundException, IOException {
        WorkerStats workerStats = (WorkerStats) si.readObject();
        String id = workerStats.getWorkerId();
        if (this.algorithmManager.workerStatsReceivedFromAutodiscovery(id, workerStats) == null) {
            log.info("Established direct connection with worker: {}", id);
        } else {
            log.info("Worker discovered but it is connected: {}", id);
        }
    }

    private void receivedWorkerStats(AsyncResult<WorkerStats> asyncResult) {
        String operationId = asyncResult.getOperationId();
        WorkerStats workerStats = asyncResult.getResult();
        String id = workerStats.getWorkerId();
        this.algorithmManager.workerStatsReceived(id, workerStats, operationId);
    }

    private void establishConnection(ObjectInputStream si) throws ClassNotFoundException, IOException {
        try {
            sharedInfrastructureManagerLock.lock();
            WorkerStats workerStats = (WorkerStats) si.readObject();
            String id = workerStats.getWorkerId();
            WorkerStats previousWorkerStats = this.infrastructureManager.getWorkers().put(id, workerStats);
            if (previousWorkerStats == null) {
                log.info("Established direct connection with worker: {}", id);
            } else {
                double launchingTime = (double) previousWorkerStats.getLastTimeFetched() / 1000;
                double runningTime = (double) workerStats.getLastTimeFetched() / 1000;
                double timeToLaunch = runningTime - launchingTime;
                this.infrastructureManager.getWorkers().get(id).setTimeToLaunch(timeToLaunch);
                log.info("=== Instance '{}' launched in {} seconds ===", workerStats.getWorkerId(), timeToLaunch);
            }
        } finally {
            sharedInfrastructureManagerLock.unlock();
        }
    }

    public void stopListen() {
        this.scheduleExecutor.shutdown();
    }

    private void runCallback(ObjectInputStream si) throws Exception {
        Algorithm<R> algorithm = (Algorithm<R>) si.readObject();
        log.info("Received solved algorithm from SQS: {}", algorithm);
        this.algorithmManager.runCallback(algorithm);
    }

    public SendMessageResult sendAlgorithm(Algorithm<R> alg) throws IOException {
        return this.sendAlgorithmHighPriority(alg);
    }

    public SendMessageResult sendAlgorithm(Algorithm<R> alg, boolean isLowPriority) throws IOException {
        if (isLowPriority) {
            return this.sendAlgorithmLowPriority(alg);
        }
        return this.sendAlgorithmHighPriority(alg);
    }

    private SendMessageResult sendAlgorithmLowPriority(Algorithm<R> alg) throws IOException {
        log.info("Sending algorithm to SQS (low priority): {}", alg);
        try {
            if (this.lowPriorityInputQueueUrl == null) {
                try {
                    this.lookForLowPriorityInputQueue();
                } catch (QueueDoesNotExistException e) {
                    log.info("Low priority input queue does not exist. Attempting to create input queue with name: {}",
                            this.lowPriorityInputQueueName);
                    this.createLowPriorityInputQueue();
                }
            }
            SendMessageResult message = this.send(this.lowPriorityInputQueueUrl, alg, MessageType.ALGORITHM);
            return message;
        } catch (QueueDoesNotExistException e) {
            this.createInputQueue();
            return sendAlgorithm(alg);
        }
    }

    private SendMessageResult sendAlgorithmHighPriority(Algorithm<R> alg) throws IOException {
            log.info("Sending algorithm to SQS: {}", alg);
        try {
            if (this.inputQueueUrl == null) {
                try {
                    this.lookForInputQueue();
                } catch (QueueDoesNotExistException e) {
                    log.info("Input queue does not exist. Attempting to create input queue with name: {}",
                            this.inputQueueName);
                    this.createInputQueue();
                }
            }
            SendMessageResult message = this.send(this.inputQueueUrl, alg, MessageType.ALGORITHM);
            return message;
        } catch (QueueDoesNotExistException e) {
            this.createInputQueue();
            return sendAlgorithm(alg);
        }
    }

    public void fetchWorkerStats(String operationId) throws IOException {
        log.info("Fetching worker stats");
        List<WorkerStats> runningWorkers = infrastructureManager.getWorkers().values().stream()
                .filter(workerStats -> workerStats.getStatus() == WorkerStatus.running)
                .filter(workerStats -> !workerStats.isDisconnected())
                .collect(Collectors.toList());
        for (WorkerStats workerStats : runningWorkers) {
            String directQueue = workerStats.directQueueUrl;
            this.asyncSend(directQueue, operationId, MessageType.FETCH_WORKER_STATS);
        }
    }

    public void fetchAlgorithmInfo(String operationId) throws IOException {
        try {
            this.sharedInfrastructureManagerLock.lock();
            log.info("Fetching current information of all algorithms");
            List<WorkerStats> runningWorkers = infrastructureManager.getWorkers().values().stream()
                    .filter(workerStats -> workerStats.getStatus() == WorkerStatus.running)
                    .filter(workerStats -> !workerStats.isDisconnected())
                    .collect(Collectors.toList());
            for (WorkerStats workerStats : runningWorkers) {
                String directQueue = workerStats.directQueueUrl;
                this.send(directQueue, operationId, MessageType.FETCH_ALG_INFO);
            }
        } finally {
            this.sharedInfrastructureManagerLock.unlock();
        }

    }

    public void terminateAlgorithms() throws IOException {
        try {
            this.sharedInfrastructureManagerLock.lock();
            log.info("Terminating all algorithms");
            List<WorkerStats> runningWorkers = infrastructureManager.getWorkers().values().stream()
                    .filter(workerStats -> workerStats.getStatus() == WorkerStatus.running)
                    .filter(workerStats -> !workerStats.isDisconnected())
                    .collect(Collectors.toList());
            for (WorkerStats workerStats : runningWorkers) {
                String directQueue = workerStats.directQueueUrl;
                this.send(directQueue, MessageType.TERMINATE_ALL, MessageType.TERMINATE_ALL);
            }
        } finally {
            this.sharedInfrastructureManagerLock.unlock();
        }

    }

    public void terminateAlgorithmsBlocking(String operationId) throws IOException {
        try {
            this.sharedInfrastructureManagerLock.lock();
            log.info("Terminating all algorithms (blocking)");
            List<WorkerStats> runningWorkers = infrastructureManager.getWorkers().values().stream()
                    .filter(workerStats -> workerStats.getStatus() == WorkerStatus.running)
                    .filter(workerStats -> !workerStats.isDisconnected())
                    .collect(Collectors.toList());
            for (WorkerStats workerStats : runningWorkers) {
                String directQueue = workerStats.directQueueUrl;
                this.send(directQueue, operationId, MessageType.TERMINATE_ALL_BLOCKING);
            }
        } finally {
            this.sharedInfrastructureManagerLock.unlock();
        }

    }

    public void stopOneAlgorithmBlocking(String algorithmId) throws IOException {
        try {
            this.sharedInfrastructureManagerLock.lock();
            List<WorkerStats> runningWorkers = infrastructureManager.getWorkers().values().stream()
                    .filter(workerStats -> workerStats.getStatus() == WorkerStatus.running)
                    .filter(workerStats -> !workerStats.isDisconnected())
                    .collect(Collectors.toList());
            for (WorkerStats workerStats : runningWorkers) {
                String directQueue = workerStats.directQueueUrl;
                this.send(directQueue, algorithmId, MessageType.TERMINATE_ONE);
            }
        } finally {
            this.sharedInfrastructureManagerLock.unlock();
        }

    }

    public void disableInputForWorkers(List<WorkerStats> workers, String operationId) throws IOException {
        try {
            this.sharedInfrastructureManagerLock.lock();
            for(WorkerStats workerStats: workers) {
                String directQueue = workerStats.getDirectQueueUrl();
                this.send(directQueue, operationId, MessageType.DISABLE_INPUT);
            }
        } finally {
            this.sharedInfrastructureManagerLock.unlock();
        }
    }

    public void enableInputForWorker(List<WorkerStats> workers) throws IOException {
        try {
            this.sharedInfrastructureManagerLock.lock();
            for(WorkerStats workerStats: workers) {
                String directQueue = workerStats.getDirectQueueUrl();
                this.send(directQueue, MessageType.ENABLE_INPUT, MessageType.ENABLE_INPUT);
            }
        } finally {
            this.sharedInfrastructureManagerLock.unlock();
        }

    }

    public void deleteDirectQueues() {
        try {
            this.sharedInfrastructureManagerLock.lock();
            log.info("Deleting direct SQS queues");
            for (WorkerStats workerStats : infrastructureManager.getWorkers().values()) {
                String queueUrl = workerStats.directQueueUrl;
                this.sqs.deleteQueue(queueUrl);
            }
        } finally {
            this.sharedInfrastructureManagerLock.unlock();
        }

    }

    public void deleteInputQueues() {
        log.info("Deleting input SQS queues: {}", this.inputQueueUrl);
        this.sqs.deleteQueue(this.inputQueueUrl);
        this.sqs.deleteQueue(this.lowPriorityInputQueueUrl);
    }

    public void deleteOutputQueues() {
        log.info("Deleting output SQS queues: {}", this.outputQueueUrl);
        this.sqs.deleteQueue(this.outputQueueUrl);
    }

    public int getNumberOfWorkers() {
        try {
            this.sharedInfrastructureManagerLock.lock();
            return Long.valueOf(this.infrastructureManager.getWorkers()
                    .values().stream()
                    .filter(workerStats -> workerStats.getStatus() == WorkerStatus.running)
                    .filter(workerStats -> !workerStats.isDisconnected)
                    .count()).intValue();
        } catch(Exception e) {
            e.printStackTrace();
            return 0;
        } finally {
            this.sharedInfrastructureManagerLock.unlock();
        }

    }
}