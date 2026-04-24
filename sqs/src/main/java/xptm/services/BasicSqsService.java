package xptm.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Basic SQS service providing fundamental operations for sending, receiving, and deleting messages.
 */
@ApplicationScoped
public class BasicSqsService {

    private final Logger logger;
    private final SqsClient sqsClient;
    private final String queueUrl;
    private final int visibilityTimeout;

    /**
     * Constructor that initializes the SQS client and retrieves the queue URL.
     *
     * @param sqsClient SqsClient instance
     * @param queueName the name of the SQS queue
     * @param visibilityTimeout the visibility timeout for messages
     * @param logger Logger instance
     */
    @Inject
    public BasicSqsService(SqsClient sqsClient,
                           @ConfigProperty(name = "queue.name") String queueName,
                           @ConfigProperty(name = "queue.visibility.timeout", defaultValue = "30") int visibilityTimeout,
                           Logger logger) {
        this.sqsClient = sqsClient;
        this.logger = logger;
        this.visibilityTimeout = visibilityTimeout;

        GetQueueUrlRequest queueUrlRequest = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();

        this.logger.infof("Retrieving Queue URL for queue [%s]", queueName);
        GetQueueUrlResponse queueUrlResponse = this.sqsClient.getQueueUrl(queueUrlRequest);
        this.queueUrl = queueUrlResponse.queueUrl();
        this.logger.infof("Queue URL retrieved: %s", this.queueUrl);
    }

    /**
     * Receives messages from the SQS queue.
     *
     * @return List of messages from the queue
     */
    public List<Message> receiveMessages() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(this.queueUrl)
                .maxNumberOfMessages(1)
                .visibilityTimeout(this.visibilityTimeout)
                .build();
        return this.sqsClient.receiveMessage(request).messages();
    }

    /**
     * Deletes a message from the SQS queue using its receipt handle.
     *
     * @param receiptHandle the receipt handle of the message to delete
     */
    public void deleteMessage(String receiptHandle) {
        this.logger.infof("Deleting message with receipt handle: %s from queue: %s", receiptHandle, queueUrl);
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();
        this.sqsClient.deleteMessage(deleteMessageRequest);
    }

    /**
     * Sends a message to the SQS queue.
     *
     * @param message the message body to send
     */
    public void sendMessage(String message) {
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
                .build();
        this.sqsClient.sendMessage(sendMessageRequest);
    }

    /**
     * Returns the queue URL.
     *
     * @return the queue URL
     */
    public String getQueueUrl() {
        return queueUrl;
    }
}