package es.codeurjc.squirrel.drey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SQSConnector<R extends Serializable> {

    private AmazonSQS sqs;

    protected String inputQueueUrl = null;
    protected String outputQueueUrl = null;

    protected final String DEFAULT_INPUT_QUEUE = "input_queue.fifo";
    protected final String DEFAULT_OUTPUT_QUEUE = "output_queue.fifo";
    protected String inputQueueName = DEFAULT_INPUT_QUEUE;
    protected String outputQueueName = DEFAULT_OUTPUT_QUEUE;

    private String messageGroupId = UUID.randomUUID().toString();

    protected AlgorithmManager<R> algorithmManager;

    protected boolean queueCreationEnabled;

    private static final Logger log = LoggerFactory.getLogger(SQSConnector.class);

    public SQSConnector(AlgorithmManager<R> algorithmManager) {
        this.algorithmManager = algorithmManager;

        this.queueCreationEnabled = System.getProperty("queue-creation-enabled") != null ? Boolean.valueOf(System.getProperty("queue-creation-enabled")) : true;

        String region = System.getProperty("aws-region") != null ? System.getProperty("aws-region") : "eu-west-1";
        String endpointUrl = System.getProperty("endpoint-url");
        if (endpointUrl != null) {
            EndpointConfiguration ec = new EndpointConfiguration(endpointUrl, region);
            this.sqs = AmazonSQSClientBuilder.standard().withEndpointConfiguration(ec).build();
        } else {
            this.sqs = AmazonSQSClientBuilder.standard().withRegion(region).build();
        }
        try {
            this.lookForInputQueue();
        } catch (QueueDoesNotExistException e) {
            log.error("Input queue does not exist: {}", this.inputQueueName);
        }
        try {
            this.lookForOutputQueue();
        } catch (QueueDoesNotExistException e) {
            log.error("Output queue does not exist: {}", this.outputQueueName);
        }
    }

    protected SendMessageResult send(String queue, Object object) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ObjectOutputStream so = new ObjectOutputStream(bo);
        so.writeObject(object);
        so.flush();
        String serializedObject = Base64.getEncoder().encodeToString(bo.toByteArray()); 
        SendMessageRequest send_msg_request = new SendMessageRequest()
            .withQueueUrl(queue)
            .withMessageGroupId(this.messageGroupId)
            .withMessageBody(serializedObject);
        SendMessageResult sentMessage = sqs.sendMessage(send_msg_request);
        log.info("Sent object to SQS queue {} with size (bytes): {}", queue, bo.size());
        return sentMessage;
    }

    protected List<ObjectInputStream> messageListener(String queue) throws IOException {
        ReceiveMessageResult messages = sqs.receiveMessage(queue);
        List<ObjectInputStream> siList = new ArrayList<>();
        for(Message message: messages.getMessages()) {
            String receiptHandle = message.getReceiptHandle();
            String messageBody = message.getBody();
            byte b[] = Base64.getDecoder().decode(messageBody.getBytes()); 
            ByteArrayInputStream bi = new ByteArrayInputStream(b);
            ObjectInputStream si = new ObjectInputStream(bi);
            log.info("Received object from SQS queue {} with size (bytes): {}", queue, b.length);
            sqs.deleteMessage(queue, receiptHandle);
            siList.add(si);
        }
        return siList;
    }

    public void createQueues() {
        createInputQueue();
        createOutputQueue();
    }

    public void createInputQueue() {
        CreateQueueRequest request = new CreateQueueRequest(this.inputQueueName);
        request.addAttributesEntry("FifoQueue", "true");
        request.addAttributesEntry("ContentBasedDeduplication ", "true");
        CreateQueueResult result = this.sqs.createQueue(request);
        this.inputQueueUrl = result.getQueueUrl();
    }

    public void createOutputQueue() {
        CreateQueueRequest request = new CreateQueueRequest(this.outputQueueName);
        request.addAttributesEntry("FifoQueue", "true");
        request.addAttributesEntry("ContentBasedDeduplication ", "true");
        CreateQueueResult result = this.sqs.createQueue(request);
        this.outputQueueUrl = result.getQueueUrl();
    }

    protected String lookForInputQueue() throws QueueDoesNotExistException {
        this.inputQueueName = System.getProperty("input-queue") != null ? System.getProperty("input-queue") : DEFAULT_INPUT_QUEUE;
        if (!this.inputQueueName.substring(this.inputQueueName.length() - 5).equals(".fifo")) {
            log.info("Input queue name does not end in .fifo, appending");
            this.inputQueueName = this.inputQueueName + ".fifo";
        }
        this.inputQueueUrl = this.sqs.getQueueUrl(inputQueueName).getQueueUrl();
        return this.inputQueueUrl;
    }
    
    protected String lookForOutputQueue() throws QueueDoesNotExistException {
        this.outputQueueName = System.getProperty("output-queue") != null ? System.getProperty("output-queue") : DEFAULT_OUTPUT_QUEUE;
        if (!this.outputQueueName.substring(this.outputQueueName.length() - 5).equals(".fifo")) {
            log.info("Output queue name does not end in .fifo, appending");
            this.outputQueueName = this.outputQueueName + ".fifo";
        }
        this.outputQueueUrl = this.sqs.getQueueUrl(outputQueueName).getQueueUrl();
        return this.outputQueueUrl;
    }
}