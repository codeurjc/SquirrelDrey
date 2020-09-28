package es.codeurjc.squirrel.drey.local;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.SendMessageResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.codeurjc.squirrel.drey.local.Algorithm.Status;

/**
 * Worker class, listens to the input queue for tasks and sends results to the
 * output queue
 */
public class SQSConnectorWorker<R extends Serializable> extends SQSConnector<R> {

    private String directQueueUrl;

    private final String DEFAULT_DIRECT_QUEUE = this.id + ".fifo";
    private String directQueueName = DEFAULT_DIRECT_QUEUE;

    private static final Logger log = LoggerFactory.getLogger(SQSConnectorWorker.class);
    private ScheduledExecutorService scheduleExecutorInput; // Local scheduled executor for running input listener thread
    private ScheduledExecutorService scheduleExecutorDirect; // Local scheduled executor for running direct listener thread
    private long listenerPeriod;

    public SQSConnectorWorker(String id, AlgorithmManager<R> algorithmManager) {
        super(id, algorithmManager);

        try {
            this.lookForDirectQueue();
        } catch (QueueDoesNotExistException e) {
            log.info("Direct queue does not exist. Attempting to create direct queue with name: {}",
                    this.directQueueName);
            this.createDirectQueue();
        }

        try {
            this.establishDirectConnection();
        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }

        // Set up thread to listen to queue
        this.listenerPeriod = System.getProperty("sqs-listener-timer") != null
                ? Integer.valueOf(System.getProperty("sqs-listener-timer"))
                : 10;
        this.scheduleExecutorInput = Executors.newScheduledThreadPool(1);
        this.startListenInput();
        this.scheduleExecutorDirect = Executors.newScheduledThreadPool(1);
        this.startListenDirect();
    }

    private void createDirectQueue() {
        CreateQueueResult result = this.createQueue(this.directQueueName);
        this.directQueueUrl = result.getQueueUrl();
    }

    private String lookForDirectQueue() throws QueueDoesNotExistException {
        this.directQueueUrl = System.getProperty("direct-queue") != null ? System.getProperty("direct-queue")
                : DEFAULT_DIRECT_QUEUE;
        if (!this.directQueueUrl.substring(this.directQueueUrl.length() - 5).equals(".fifo")) {
            log.info("Direct queue name does not end in .fifo, appending");
            this.directQueueUrl = this.directQueueUrl + ".fifo";
        }
        this.directQueueUrl = this.sqs.getQueueUrl(directQueueUrl).getQueueUrl();
        return this.directQueueUrl;
    }

    private SendMessageResult establishDirectConnection() throws IOException, InterruptedException {
        if (this.directQueueUrl == null) {
            try {
                this.lookForDirectQueue();
            } catch (QueueDoesNotExistException e) {
                int retryTime = 1000;
                log.error("Direct queue does not exist. Retrying in: {} ms", retryTime);
                Thread.sleep(retryTime);
                return establishDirectConnection();
            }
        }
        log.info("Establishing direct connection with master via SQS: {}", this.directQueueUrl);
        SendMessageResult message = null;
        try {
            message = this.send(this.outputQueueUrl, this.directQueueUrl,
                MessageType.ESTABLISH_CONNECTION);
        } catch (QueueDoesNotExistException e) {
            int retryTime = 1000;
            log.error("Direct queue does not exist. Retrying in: {} ms", retryTime);
            Thread.sleep(retryTime);
            return establishDirectConnection();
        }
        return message;
    }

    public void startListenInput() {
        this.scheduleExecutorInput.scheduleAtFixedRate(() -> {
            boolean runningAlg = false;
            for (Map.Entry<String, Algorithm<R>> algEntry: this.algorithmManager.algorithms.entrySet()) {
                if (algEntry.getValue().getStatus() == Algorithm.Status.STARTED) {
                    runningAlg = true;
                    break;
                }
            }
            if (!runningAlg) {
                try {
                    if (this.inputQueueUrl == null) {
                        this.lookForInputQueue();
                    }
                    Map<ObjectInputStream, Map<String, MessageAttributeValue>> siMap = messageListener(this.inputQueueUrl);
                    for (Map.Entry<ObjectInputStream, Map<String, MessageAttributeValue>> si : siMap.entrySet()) {
                        switch (Enum.valueOf(MessageType.class, si.getValue().get("Type").getStringValue())) {
                            case ALGORITHM: {
                                solveAlgorithm(si.getKey());
                                break;
                            }
                            default:
                                throw new Exception("Incorrect message type received in worker: "
                                        + si.getValue().get("Type").getStringValue());
                        }
                    }
                } catch (QueueDoesNotExistException ex) {
                    log.error(ex.getMessage());
                } catch (Exception e) {
                    log.error(e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 0, listenerPeriod, TimeUnit.SECONDS);
    }

    public void startListenDirect() {
        this.scheduleExecutorDirect.scheduleAtFixedRate(() -> {
            try {
                if (this.directQueueUrl == null) {
                    this.lookForDirectQueue();
                }
                Map<ObjectInputStream, Map<String, MessageAttributeValue>> siMap = messageListener(this.directQueueUrl);
                for (Map.Entry<ObjectInputStream, Map<String, MessageAttributeValue>> si : siMap.entrySet()) {
                    switch (Enum.valueOf(MessageType.class, si.getValue().get("Type").getStringValue())) {
                        case FETCH_WORKER_STATS:
                            retrieveWorkerStats();
                            break;
                        case TERMINATE_ALL:
                            terminateAllAlgorithms();
                            break;
                        case TERMINATE_ALL_BLOCKING:
                            terminateAllAlgorithmsBlocking();
                            break;
                        case TERMINATE_ONE:
                            terminateOneAlgorithmBlocking(si.getKey());
                            break;
                        case FETCH_ALG_INFO:
                            retrieveAlgInfo();
                            break;
                        default:
                            throw new Exception("Incorrent message type received in worker: "
                                    + si.getValue().get("Type").getStringValue());
                    }
                }
            } catch (QueueDoesNotExistException e) {
                log.info("Direct queue does not exist. Attempting to create direct queue with name: {}",
                        this.directQueueName);
                this.createDirectQueue();
            } catch (Exception e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }
        }, 10, listenerPeriod, TimeUnit.SECONDS);
    }

    private List<Algorithm<R>> terminateAllAlgorithmsBlocking() throws IOException, InterruptedException {
        List<Algorithm<R>> algs = this.algorithmManager.terminateAlgorithmsBlockingWorker();
        this.sendTerminateAllAlgorithmsBlocking(algs);
        return algs;
    }

    private SendMessageResult sendTerminateAllAlgorithmsBlocking(List<Algorithm<R>> algs)
            throws IOException, InterruptedException {
        if (this.outputQueueUrl == null) {
            try {
                this.lookForOutputQueue();
            } catch (QueueDoesNotExistException e) {
                int retryTime = 1000;
                log.error("Output queue does not exist. Retrying in: {} ms", retryTime);
                Thread.sleep(retryTime);
                return sendTerminateAllAlgorithmsBlocking(algs);
            }
        }
        log.info("Sending terminate all algorithms blocking done: {}", algs);
        SendMessageResult message = this.send(this.outputQueueUrl, algs, MessageType.TERMINATE_ALL_DONE);
        return message;
    }

    private List<Algorithm<R>> terminateAllAlgorithms() {
        return this.algorithmManager.terminateAlgorithmsWorker();
    }

    private Algorithm<R> terminateOneAlgorithmBlocking(ObjectInputStream si)
            throws IOException, InterruptedException, ClassNotFoundException {
        String algorithmId = (String) si.readObject();
        Algorithm<R> alg = this.algorithmManager.terminateOneAlgorithmBlockingWorker(algorithmId);
        this.sendTerminateOneAlgorithmBlocking(alg);
        return alg;
    }

    public SendMessageResult sendTerminateOneAlgorithmBlocking(Algorithm<R> alg)
            throws InterruptedException, IOException {
        if (this.outputQueueUrl == null) {
            try {
                this.lookForOutputQueue();
            } catch (QueueDoesNotExistException e) {
                int retryTime = 1000;
                log.error("Output queue does not exist. Retrying in: {} ms", retryTime);
                Thread.sleep(retryTime);
                return sendTerminateOneAlgorithmBlocking(alg);
            }
        }
        log.info("Sending terminate one algorithm blocking done: {}", alg);
        SendMessageResult message = this.send(this.outputQueueUrl, alg, MessageType.TERMINATE_ONE_DONE);
        return message;
    }

    public void stopListen() {
        this.scheduleExecutorInput.shutdown();
        this.scheduleExecutorDirect.shutdown();
    }

    private void solveAlgorithm(ObjectInputStream si) throws Exception {
        Algorithm<R> algorithm = (Algorithm<R>) si.readObject();
        log.info("Received algorithm from SQS: {}", algorithm);
        this.algorithmManager.solveAlgorithmAux(algorithm.getId(), algorithm);
    }

    private SendMessageResult retrieveWorkerStats() throws InterruptedException, IOException {
        if (this.outputQueueUrl == null) {
            try {
                this.lookForOutputQueue();
            } catch (QueueDoesNotExistException e) {
                int retryTime = 1000;
                log.error("Output queue does not exist. Retrying in: {} ms", retryTime);
                Thread.sleep(retryTime);
                return retrieveWorkerStats();
            }
        }
        WorkerStats result = this.algorithmManager.getWorkerStats();
        log.info("Sending worker stats: {}", result);
        SendMessageResult message = this.send(this.outputQueueUrl, result, MessageType.WORKER_STATS);
        return message;
    }

    public SendMessageResult sendResult(Algorithm<R> result) throws Exception {
        if (this.outputQueueUrl == null) {
            try {
                this.lookForOutputQueue();
            } catch (QueueDoesNotExistException e) {
                int retryTime = 1000;
                log.error("Output queue does not exist. Retrying in: {} ms", retryTime);
                Thread.sleep(retryTime);
                return sendResult(result);
            }
        }
        log.info("Sending solved algorithm to SQS: {}", result);
        SendMessageResult message = this.send(this.outputQueueUrl, result, MessageType.RESULT);
        return message;
    }

	public SendMessageResult sendError(Algorithm<R> alg, Status reason) throws InterruptedException, IOException {
        if (this.outputQueueUrl == null) {
            try {
                this.lookForOutputQueue();
            } catch (QueueDoesNotExistException e) {
                int retryTime = 1000;
                log.error("Output queue does not exist. Retrying in: {} ms", retryTime);
                Thread.sleep(retryTime);
                return sendError(alg, reason);
            }
        }
        Map<Algorithm<R>, Status> algReasonMap = new HashMap<>(1);
        algReasonMap.put(alg, reason);
        log.info("Sending error in algorithm to SQS: , with reason: {}", alg, reason);
        SendMessageResult message = this.send(this.outputQueueUrl, algReasonMap, MessageType.ERROR);
        return message;
    }
    
    private SendMessageResult retrieveAlgInfo() throws InterruptedException, IOException {
        if (this.outputQueueUrl == null) {
            try {
                this.lookForOutputQueue();
            } catch (QueueDoesNotExistException e) {
                int retryTime = 1000;
                log.error("Output queue does not exist. Retrying in: {} ms", retryTime);
                Thread.sleep(retryTime);
                return retrieveAlgInfo();
            }
        }
        List<AlgorithmInfo> result = this.algorithmManager.getAlgorithmInfoWorker();
        log.info("Sending algorithm information: {}", result);
        SendMessageResult message = this.send(this.outputQueueUrl, result, MessageType.ALG_INFO);
        return message;
    }
}