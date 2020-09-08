package es.codeurjc.squirrel.drey;

import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.SendMessageResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Master class, listens to the output queue for results and sends tasks to the input queue
 */
public class SQSConnectorMaster<R extends Serializable> extends SQSConnector<R> {

    private Map<String, String> directQueuesUrls = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(SQSConnectorMaster.class);
    private ScheduledExecutorService scheduleExecutor; // Local scheduled executor for running listener thread
    private long listenerPeriod;

    public SQSConnectorMaster(AlgorithmManager<R> algorithmManager) {
        super(algorithmManager);

        // Set up thread to listen to queue
        this.listenerPeriod = System.getProperty("sqs-listener-timer") != null ? Integer.valueOf(System.getProperty("sqs-listener-timer")) : 10;
        this.scheduleExecutor = Executors.newScheduledThreadPool(1);
        this.startListen();
    }

    public void startListen() {
        this.scheduleExecutor.scheduleAtFixedRate(() -> {
            try {
                if (this.outputQueueUrl == null) {
                    this.lookForOutputQueue();
                }
            } catch (QueueDoesNotExistException e) {
                log.info("Output queue does not exist. Attempting to create output queue with name: {}", this.outputQueueName);
                this.createOutputQueue();
            }
            try {
                Map<ObjectInputStream, Map<String, MessageAttributeValue>> siMap = messageListener(this.outputQueueUrl);
                for (Map.Entry<ObjectInputStream, Map<String, MessageAttributeValue>> si : siMap.entrySet()) {
                    switch (Enum.valueOf(MessageType.class, si.getValue().get("Type").getStringValue())) {
                        case RESULT: 
                            runCallback(si.getKey());
                            break;
                        case ESTABLISH_CONNECTION:
                            establishConnection(si.getKey(), si.getValue().get("Id").getStringValue());
                            break;
                        case WORKER_STATS:
                            receivedWorkerStats(si.getKey(), si.getValue().get("Id").getStringValue());
                            break;
                        case TERMINATE_ALL_DONE:
                            this.algorithmManager.stopAlgorithmsDone();
                            break;
                        case TERMINATE_ONE_DONE:
                            this.algorithmManager.stopOneAlgorithmDone((String) si.getKey().readObject());
                            break;
                        default:
                            throw new Exception("Incorrent message type received in master: " + si.getValue().get("Type").getStringValue());
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }
        }, 0, listenerPeriod, TimeUnit.SECONDS);
    }

    private void receivedWorkerStats(ObjectInputStream si, String id) throws ClassNotFoundException, IOException {
        WorkerStats workerStats = (WorkerStats) si.readObject();
        this.algorithmManager.workerStatsReceived(id, workerStats);
    }

    private void establishConnection(ObjectInputStream si, String id) throws ClassNotFoundException, IOException {
        String directQueueUrl = (String) si.readObject();
        if (this.directQueuesUrls.put(id, directQueueUrl) == null) {
            log.info("Established direct connection with worker: {}", id);
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
        log.info("Sending algorithm to SQS: {}", alg);
        try {
            if (this.inputQueueUrl == null) {
                try {
                    this.lookForInputQueue();
                } catch (QueueDoesNotExistException e) {
                    log.info("Input queue does not exist. Attempting to create input queue with name: {}", this.inputQueueName);
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
        for (String directQueue : this.directQueuesUrls.values()) {
            this.send(directQueue, MessageType.FETCH_WORKER_STATS, MessageType.FETCH_WORKER_STATS);
        }
    }

    public void terminateAlgorithms() throws IOException {
        log.info("Terminating all algorithms");
        for (String directQueue : this.directQueuesUrls.values()) {
            this.send(directQueue, MessageType.TERMINATE_ALL, MessageType.TERMINATE_ALL);
        }
    }

	public void terminateAlgorithmsBlocking() throws IOException {
        log.info("Terminating all algorithms (blocking)");
        for (String directQueue : this.directQueuesUrls.values()) {
            this.send(directQueue, MessageType.TERMINATE_ALL_BLOCKING, MessageType.TERMINATE_ALL_BLOCKING);
        }
	}

	public void stopOneAlgorithmBlocking(String algorithmId) throws IOException {
        log.info("Terminating algorithm: {}", algorithmId);
        for (String directQueue : this.directQueuesUrls.values()) {
            this.send(directQueue, algorithmId, MessageType.TERMINATE_ONE);
        }
    }
    
    public void deleteDirectQueues() {
        log.info("Deleting direct SQS queues: {}", this.directQueuesUrls.values());
        for (String queueUrl : this.directQueuesUrls.values()) {
            this.sqs.deleteQueue(queueUrl);
        }
    }

    public void deleteInputQueues() {
        log.info("Deleting input SQS queues: {}", this.inputQueueUrl);
        this.sqs.deleteQueue(this.inputQueueUrl);
    }

    public void deleteOutputQueues() {
        log.info("Deleting output SQS queues: {}", this.outputQueueUrl);
        this.sqs.deleteQueue(this.outputQueueUrl);
    }

    public int getNumberOfWorkers() {
        return this.directQueuesUrls.size();
    }
}