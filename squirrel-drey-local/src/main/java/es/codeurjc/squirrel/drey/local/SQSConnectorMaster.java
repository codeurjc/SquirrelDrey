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


import es.codeurjc.squirrel.drey.local.autoscaling.InfrastructureManager;
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
    private int listenerPeriod;
    private ReentrantLock sharedinfrastructureManagerLock;
    private InfrastructureManager<R> infrastructureManager;
    private AlgorithmManager<R> algorithmManager;

    public SQSConnectorMaster(AlgorithmManager<R> algorithmManager, ReentrantLock sharedinfrastructureManagerLock) {
        super(UUID.randomUUID().toString());
        this.algorithmManager = algorithmManager;
        this.sharedinfrastructureManagerLock = sharedinfrastructureManagerLock;
        this.infrastructureManager = this.algorithmManager.infrastructureManager;
        this.scheduleExecutor = Executors.newScheduledThreadPool(1);
        this.listenerPeriod = System.getProperty("sqs-listener-timer") != null
                ? Integer.valueOf(System.getProperty("sqs-listener-timer"))
                : 1;
        try {
            this.autodiscoverWorkers();
        } catch (IOException e) {
            log.error("Error while autodiscovering workers: {}", e.getMessage());
        }
        this.startListen();
    }

    public void startListen() {
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
                            establishConnection(si.getKey(), si.getValue().get("Id").getStringValue());
                            break;
                        }
                        case WORKER_STATS_AUTODISCOVERY:
                            receivedWorkerStatsFromAutodiscovery(si.getKey(), si.getValue().get("Id").getStringValue());
                            break;
                        case WORKER_STATS: {
                            receivedWorkerStats(si.getKey(), si.getValue().get("Id").getStringValue());
                            break;
                        }
                        case TERMINATE_ALL_DONE: {
                            this.algorithmManager.stopAlgorithmsDone();
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
                            receivedAlgorithmInfo(si.getKey());
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
        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    private void autodiscoverWorkers() throws IOException {
        ListQueuesRequest listQueuesRequest = new ListQueuesRequest(this.directQueuePrefix);
        ListQueuesResult listQueuesResult = sqs.listQueues(listQueuesRequest);
        for(String directQueueUrl: listQueuesResult.getQueueUrls()) {
            this.send(directQueueUrl, MessageType.AUTODISCOVER_FROM_MASTER, MessageType.AUTODISCOVER_FROM_MASTER);
        }
    }

    private void receivedAlgorithmInfo(ObjectInputStream si) throws ClassNotFoundException, IOException {
        List<AlgorithmInfo> algInfo = (List<AlgorithmInfo>) si.readObject();
        this.algorithmManager.algorithmInfoReceived(algInfo);
    }

    private void runCallbackError(ObjectInputStream si) throws Exception {
        Map<Algorithm<R>, Status> erroredAlgMap = (HashMap<Algorithm<R>, Status>) si.readObject();
        Map.Entry<Algorithm<R>, Status> erroredAlgEntry = erroredAlgMap.entrySet().iterator().next();
        this.algorithmManager.runCallbackError(erroredAlgEntry.getKey(), erroredAlgEntry.getValue());
    }

    private void receivedWorkerStatsFromAutodiscovery(ObjectInputStream si, String id) throws ClassNotFoundException, IOException {
        WorkerStats workerStats = (WorkerStats) si.readObject();
        if (this.algorithmManager.workerStatsReceivedFromAutodiscovery(id, workerStats) == null) {
            log.info("Established direct connection with worker: {}", id);
        } else {
            log.info("Worker discovered but it is connected: {}", id);
        }
    }

    private void receivedWorkerStats(ObjectInputStream si, String id) throws ClassNotFoundException, IOException {
        WorkerStats workerStats = (WorkerStats) si.readObject();
        this.algorithmManager.workerStatsReceived(id, workerStats);
    }

    private void establishConnection(ObjectInputStream si, String id) throws ClassNotFoundException, IOException {
        try {
            sharedinfrastructureManagerLock.lock();
            WorkerStats workerStats = (WorkerStats) si.readObject();
            if (this.infrastructureManager.getWorkers().put(id, workerStats) == null) {
                log.info("Established direct connection with worker: {}", id);
            }
        } finally {
            sharedinfrastructureManagerLock.unlock();
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

    public void fetchWorkerStats() throws IOException {
        log.info("Fetching worker stats");
        List<WorkerStats> runningWorkers = infrastructureManager.getWorkers().values()
                .stream().filter(workerStats -> workerStats.getStatus() == WorkerStatus.running).collect(Collectors.toList());
        for (WorkerStats workerStats : runningWorkers) {
            String directQueue = workerStats.directQueueUrl;
            this.send(directQueue, MessageType.FETCH_WORKER_STATS, MessageType.FETCH_WORKER_STATS);
        }
    }

    public void fetchAlgorithmInfo() throws IOException {
        try {
            this.sharedinfrastructureManagerLock.lock();
            log.info("Fetching current information of all algorithms");
            for (WorkerStats workerStats : infrastructureManager.getWorkers().values()) {
                String directQueue = workerStats.directQueueUrl;
                this.send(directQueue, MessageType.FETCH_ALG_INFO, MessageType.FETCH_ALG_INFO);
            }
        } finally {
            this.sharedinfrastructureManagerLock.unlock();
        }

    }

    public void terminateAlgorithms() throws IOException {
        try {
            this.sharedinfrastructureManagerLock.lock();
            log.info("Terminating all algorithms");
            for (WorkerStats workerStats : infrastructureManager.getWorkers().values()) {
                String directQueue = workerStats.directQueueUrl;
                this.send(directQueue, MessageType.TERMINATE_ALL, MessageType.TERMINATE_ALL);
            }
        } finally {
            this.sharedinfrastructureManagerLock.unlock();
        }

    }

    public void terminateAlgorithmsBlocking() throws IOException {
        try {
            this.sharedinfrastructureManagerLock.lock();
            log.info("Terminating all algorithms (blocking)");
            for (WorkerStats workerStats : infrastructureManager.getWorkers().values()) {
                String directQueue = workerStats.directQueueUrl;
                this.send(directQueue, MessageType.TERMINATE_ALL_BLOCKING, MessageType.TERMINATE_ALL_BLOCKING);
            }
        } finally {
            this.sharedinfrastructureManagerLock.unlock();
        }

    }

    public void stopOneAlgorithmBlocking(String algorithmId) throws IOException {
        try {
            this.sharedinfrastructureManagerLock.lock();
            for (WorkerStats workerStats : infrastructureManager.getWorkers().values()) {
                String directQueue = workerStats.directQueueUrl;
                this.send(directQueue, algorithmId, MessageType.TERMINATE_ONE);
            }
        } finally {
            this.sharedinfrastructureManagerLock.unlock();
        }

    }

    public void deleteDirectQueues() {
        try {
            this.sharedinfrastructureManagerLock.lock();
            log.info("Deleting direct SQS queues");
            for (WorkerStats workerStats : infrastructureManager.getWorkers().values()) {
                String queueUrl = workerStats.directQueueUrl;
                this.sqs.deleteQueue(queueUrl);
            }
        } finally {
            this.sharedinfrastructureManagerLock.unlock();
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
            this.sharedinfrastructureManagerLock.lock();
            return Long.valueOf(this.infrastructureManager.getWorkers()
                    .values().stream()
                    .filter(workerStats -> workerStats.getStatus() == WorkerStatus.running)
                    .count()).intValue();
        } catch(Exception e) {
            e.printStackTrace();
            return 0;
        } finally {
            this.sharedinfrastructureManagerLock.unlock();
        }

    }
}