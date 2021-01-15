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

    private final int DEFAULT_PARALLELIZATION_GRADE = 1;
    private int parallelizationGrade = DEFAULT_PARALLELIZATION_GRADE;

    private static final Logger log = LoggerFactory.getLogger(SQSConnectorWorker.class);
    private ScheduledExecutorService scheduleExecutorOuput; // Local scheduled executor for running listener thread
    private ScheduledExecutorService scheduleExecutorDirect; // Local scheduled executor for running listener thread

    private AlgorithmManager<R> algorithmManager;

    public SQSConnectorWorker(Config config, String id, AlgorithmManager<R> algorithmManager) {
        super(config, id);
        this.algorithmManager = algorithmManager;
        this.parallelizationGrade = config.getParallelizationGrade();

        try {
            this.lookForDirectQueue();
        } catch (QueueDoesNotExistException e) {
            log.info("Direct queue does not exist. Attempting to create direct queue with name: {}",
                    this.directQueueName);
            this.createDirectQueue();
        }

        this.scheduleExecutorOuput = Executors.newScheduledThreadPool(1);
        this.scheduleExecutorDirect = Executors.newScheduledThreadPool(1);
        this.startListen();
    }

    private void createDirectQueue() {
        CreateQueueResult result = this.createQueue(this.directQueueName);
        this.directQueueUrl = result.getQueueUrl();
    }

    private String lookForDirectQueue() throws QueueDoesNotExistException {
        this.directQueueName = this.directQueuePrefix + "_" + this.id;
        if (!this.directQueueName.endsWith(".fifo")) {
            log.info("Direct queue name does not end in .fifo, appending");
            this.directQueueName = this.directQueueName + ".fifo";
        }
        this.directQueueUrl = this.sqs.getQueueUrl(this.directQueueName).getQueueUrl();
        return this.directQueueUrl;
    }

    protected SendMessageResult establishDirectConnection(boolean fromAutodiscover) throws IOException, InterruptedException {
        if (this.directQueueUrl == null) {
            try {
                this.lookForDirectQueue();
            } catch (QueueDoesNotExistException e) {
                int retryTime = 1000;
                log.error("Direct queue does not exist. Retrying in: {} ms", retryTime);
                Thread.sleep(retryTime);
                return establishDirectConnection(fromAutodiscover);
            }
        }

        WorkerStats workerStats = this.algorithmManager.getWorkerStats();

        SendMessageResult message = null;
        try {
            if (fromAutodiscover) {
                log.info("Master discovered this worker. Worker is establishing direct connection with master via SQS: {}", this.directQueueUrl);
                message = this.send(this.outputQueueUrl, workerStats, MessageType.WORKER_STATS_AUTODISCOVERY);
            } else {
                log.info("Worker is establishing direct connection with master via SQS: {}", this.directQueueUrl);
                message = this.send(this.outputQueueUrl, workerStats, MessageType.ESTABLISH_CONNECTION);
            }

        } catch (QueueDoesNotExistException e) {
            int retryTime = 1000;
            log.error("Output queue does not exist. Retrying in: {} ms", retryTime);
            Thread.sleep(retryTime);
            return establishDirectConnection(fromAutodiscover);
        }
        return message;
    }

    public void startListen() {
        this.scheduleExecutorOuput.scheduleAtFixedRate(() -> {
            listenInput();
        }, 0, 500, TimeUnit.MILLISECONDS);

        this.scheduleExecutorDirect.scheduleAtFixedRate(() -> {
            listenDirect();
        }, 0, this.sqsListenerPeriod, TimeUnit.MILLISECONDS);
    }

    public void listenInput() {
        boolean runningAlg = false;
        int runningAlgs = 0;
        // If algorithms is null it hasn't finished initializing algorithm manager
        if (this.algorithmManager.algorithms != null) {
            for (Map.Entry<String, Algorithm<R>> algEntry : this.algorithmManager.algorithms.entrySet()) {
                if (algEntry.getValue().getStatus() == Algorithm.Status.STARTED) {
                    runningAlgs++;
                    if (runningAlgs >= this.parallelizationGrade) {
                        log.info("Max Number of Algorithms Running, input messages will not be checked.");
                        runningAlg = true;
                        break;
                    }
                }
            }
        } else {
            // Wait until algorithm manager is initialized
            runningAlg = true;
        }
        if (!runningAlg) {
            try {
                if (this.inputQueueUrl == null) {
                    this.lookForInputQueue();
                }
                Map<ObjectInputStream, Map<String, MessageAttributeValue>> siMap = messageListener(this.inputQueueUrl);
                if (siMap.size() > 0) {
                    processMessage(siMap);
                } else if (runningAlgs <= 0) {
                    if (this.lowPriorityInputQueueUrl == null) {
                        this.lookForLowPriorityInputQueue();
                    }
                    Map<ObjectInputStream, Map<String, MessageAttributeValue>> lowPrioSiMap = messageListener(this.lowPriorityInputQueueUrl);
                    processMessage(lowPrioSiMap);
                }
            } catch (QueueDoesNotExistException ex) {
                log.error(ex.getMessage());
            } catch (Exception e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void processMessage(Map<ObjectInputStream, Map<String, MessageAttributeValue>> messageMap)
            throws Exception {
        for (Map.Entry<ObjectInputStream, Map<String, MessageAttributeValue>> si : messageMap.entrySet()) {
            switch (Enum.valueOf(MessageType.class, si.getValue().get("Type").getStringValue())) {
                case ALGORITHM: {
                    solveAlgorithm(si.getKey());
                    break;
                }
                default:
                    throw new Exception(
                            "Incorrect message type received in worker: " + si.getValue().get("Type").getStringValue());
            }
        }
    }

    public void listenDirect() {
        try {
            if (this.directQueueUrl == null) {
                this.lookForDirectQueue();
            }
            Map<ObjectInputStream, Map<String, MessageAttributeValue>> siMap = messageListener(this.directQueueUrl);
            for (Map.Entry<ObjectInputStream, Map<String, MessageAttributeValue>> si : siMap.entrySet()) {
                switch (Enum.valueOf(MessageType.class, si.getValue().get("Type").getStringValue())) {
                    case AUTODISCOVER_FROM_MASTER:
                        establishDirectConnection(true);
                        break;
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
        SendMessageResult message = this.send(this.outputQueueUrl, alg.getId(), MessageType.TERMINATE_ONE_DONE);
        return message;
    }

    public void stopListen() {
        this.scheduleExecutorOuput.shutdown();
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