package es.codeurjc.squirrel.drey;

import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.SendMessageResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Master class, listens to the output queue for results and sends tasks to the input queue
 */
public class SQSConnectorMaster<R extends Serializable> extends SQSConnector<R>{

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
            SendMessageResult message = this.send(this.inputQueueUrl, alg);
            return message;
        } catch (QueueDoesNotExistException e) {
            this.createInputQueue();
            return sendAlgorithm(alg);
        }
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
                List<ObjectInputStream> siList = messageListener(outputQueueUrl);
                for (ObjectInputStream si : siList) {
                    Algorithm<R> algorithm = (Algorithm<R>) si.readObject();
                    log.info("Received solved algorithm from SQS: {}", algorithm);
                    this.algorithmManager.runCallback(algorithm);
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