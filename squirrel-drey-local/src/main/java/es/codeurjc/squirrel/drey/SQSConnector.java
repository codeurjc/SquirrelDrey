package es.codeurjc.squirrel.drey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import java.util.Base64;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SQSConnector<R extends Serializable> {
    protected enum MessageType {
        ESTABLISH_CONNECTION, ALGORITHM, RESULT, FETCH_WORKER_STATS, WORKER_STATS, TERMINATE_ALL,
        TERMINATE_ALL_BLOCKING, TERMINATE_ALL_DONE, TERMINATE_ONE, TERMINATE_ONE_DONE, ERROR, FETCH_ALG_INFO, ALG_INFO
    }

    protected AmazonSQS sqs;

    protected String inputQueueUrl = null;
    protected String outputQueueUrl = null;

    protected final String DEFAULT_INPUT_QUEUE = "input_queue.fifo";
    protected final String DEFAULT_OUTPUT_QUEUE = "output_queue.fifo";
    protected String inputQueueName = DEFAULT_INPUT_QUEUE;
    protected String outputQueueName = DEFAULT_OUTPUT_QUEUE;

    protected String id;

    protected AlgorithmManager<R> algorithmManager;

    protected boolean queueCreationEnabled;

    private static final Logger log = LoggerFactory.getLogger(SQSConnector.class);

    public SQSConnector(String id, AlgorithmManager<R> algorithmManager) {
        this.algorithmManager = algorithmManager;
        this.id = id;

        this.queueCreationEnabled = System.getProperty("queue-creation-enabled") != null
                ? Boolean.valueOf(System.getProperty("queue-creation-enabled"))
                : true;

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

    protected SendMessageResult send(String queue, Object object, MessageType messageType) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ObjectOutputStream so = new ObjectOutputStream(bo);
        so.writeObject(object);
        so.flush();
        String serializedObject = Base64.getEncoder().encodeToString(bo.toByteArray());
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put("Id", new MessageAttributeValue().withDataType("String").withStringValue(this.id));
        attributes.put("Type",
                new MessageAttributeValue().withDataType("String").withStringValue(messageType.toString()));
        SendMessageRequest send_msg_request = new SendMessageRequest().withQueueUrl(queue).withMessageGroupId(this.id)
                .withMessageBody(serializedObject).withMessageAttributes(attributes);
        SendMessageResult sentMessage = sqs.sendMessage(send_msg_request);
        log.info("Sent object to SQS queue {} with size (bytes): {}", queue, bo.size());
        return sentMessage;
    }

    protected Map<ObjectInputStream, Map<String, MessageAttributeValue>> messageListener(String queue)
            throws IOException {
        return this.messageListenerAux(queue, true);
    }

    protected Map<ObjectInputStream, Map<String, MessageAttributeValue>> messageListener(String queue,
            boolean deleteMsg) throws IOException {
        return this.messageListenerAux(queue, deleteMsg);
    }

    private Map<ObjectInputStream, Map<String, MessageAttributeValue>> messageListenerAux(String queue,
            boolean deleteMsg) throws IOException {
        ReceiveMessageRequest request = new ReceiveMessageRequest(queue);
        request.setMaxNumberOfMessages(1);
        ReceiveMessageResult messages = sqs.receiveMessage(request);
        Map<ObjectInputStream, Map<String, MessageAttributeValue>> siMap = new HashMap<>();
        for (Message message : messages.getMessages()) {
            String receiptHandle = message.getReceiptHandle();
            String messageBody = message.getBody();
            byte b[] = Base64.getDecoder().decode(messageBody.getBytes());
            ByteArrayInputStream bi = new ByteArrayInputStream(b);
            ObjectInputStream si = new ObjectInputStream(bi);
            log.info("Received object from SQS queue {} with size (bytes): {}", queue, b.length);
            sqs.deleteMessage(queue, receiptHandle);
            siMap.put(si, message.getMessageAttributes());
        }
        return siMap;
    }

    public void createQueues() {
        createInputQueue();
        createOutputQueue();
    }

    public void createInputQueue() {
        CreateQueueResult result = this.createQueue(this.inputQueueName);
        this.inputQueueUrl = result.getQueueUrl();
    }

    public void createOutputQueue() {
        CreateQueueResult result = this.createQueue(this.outputQueueName);
        this.outputQueueUrl = result.getQueueUrl();
    }

    protected CreateQueueResult createQueue(String queueName) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("FifoQueue", "true");
        attributes.put("ContentBasedDeduplication ", "true");
        return this.createQueueAux(queueName, attributes);
    }

    protected CreateQueueResult createQueue(String queueName, Map<String, String> attributes) {
        attributes.put("FifoQueue", "true");
        attributes.put("ContentBasedDeduplication ", "true");
        return this.createQueueAux(queueName, attributes);
    }

    private CreateQueueResult createQueueAux(String queueName, Map<String, String> attributes) {
        CreateQueueRequest request = new CreateQueueRequest(queueName);
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            request.addAttributesEntry(attribute.getKey(), attribute.getValue());
        }
        CreateQueueResult result = this.sqs.createQueue(request);
        return result;
    }

    protected String lookForInputQueue() throws QueueDoesNotExistException {
        this.inputQueueName = System.getProperty("input-queue") != null ? System.getProperty("input-queue")
                : DEFAULT_INPUT_QUEUE;
        if (!this.inputQueueName.substring(this.inputQueueName.length() - 5).equals(".fifo")) {
            log.info("Input queue name does not end in .fifo, appending");
            this.inputQueueName = this.inputQueueName + ".fifo";
        }
        this.inputQueueUrl = this.sqs.getQueueUrl(inputQueueName).getQueueUrl();
        return this.inputQueueUrl;
    }

    protected String lookForOutputQueue() throws QueueDoesNotExistException {
        this.outputQueueName = System.getProperty("output-queue") != null ? System.getProperty("output-queue")
                : DEFAULT_OUTPUT_QUEUE;
        if (!this.outputQueueName.substring(this.outputQueueName.length() - 5).equals(".fifo")) {
            log.info("Output queue name does not end in .fifo, appending");
            this.outputQueueName = this.outputQueueName + ".fifo";
        }
        this.outputQueueUrl = this.sqs.getQueueUrl(outputQueueName).getQueueUrl();
        return this.outputQueueUrl;
    }
}