package es.codeurjc.squirrel.drey;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.SendMessageResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker class, listens to the input queue for tasks and sends results to the output queue
 */
public class SQSConnectorWorker<R extends Serializable> extends SQSConnector<R> {

    private static final Logger log = LoggerFactory.getLogger(SQSConnectorWorker.class);
    private ScheduledExecutorService scheduleExecutor; // Local scheduled executor for running listener thread
    private long listenerPeriod;

    public SQSConnectorWorker(AlgorithmManager<R> algorithmManager) {
        super(algorithmManager);

        // Set up thread to listen to queue
        this.listenerPeriod = System.getProperty("sqs-listener-timer") != null ? Integer.valueOf(System.getProperty("sqs-listener-timer")) : 10;
        this.scheduleExecutor = Executors.newScheduledThreadPool(1);
        this.startListen();
    }

    public SendMessageResult sendResult(Algorithm<R> result) throws Exception {
        if (this.outputQueueUrl == null) {
            try {
                this.lookForOutputQueue();
            } catch (QueueDoesNotExistException e) {
                int retryTime = 1000;
                log.error("Output queue does not exist. Retrying in: {} ms", retryTime);
                Thread.sleep(retryTime);
                sendResult(result);
            }
            
        }
        log.info("Sending solved algorithm to SQS: {}", result);
        SendMessageResult message = this.send(this.outputQueueUrl, result);
        return message;
    }

    public void startListen() {
        this.scheduleExecutor.scheduleAtFixedRate(() -> {
            try {
                if (this.inputQueueUrl == null) {
                    this.lookForInputQueue();
                }
                List<ObjectInputStream> siList = messageListener(this.inputQueueUrl);
                for (ObjectInputStream si : siList) {
                    Algorithm<R> algorithm = (Algorithm<R>) si.readObject();
                    log.info("Received algorithm from SQS: {}", algorithm);
                    this.algorithmManager.solveAlgorithmAux(algorithm.getId(), algorithm);
                }
            } catch (Exception e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }
        }, 0, listenerPeriod, TimeUnit.SECONDS);
        
    }

    public void stopListen() {
        this.scheduleExecutor.shutdown();
    }
}