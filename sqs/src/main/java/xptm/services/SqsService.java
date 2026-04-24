package xptm.services;

import com.amazon.sqs.javamessaging.AmazonSQSExtendedClient;
import com.amazon.sqs.javamessaging.ExtendedClientConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * This class works as a central location for all AWS services connections
 * used inside this project.
 */
@ApplicationScoped
public class SqsService {
  private final Logger logger;
  private final AmazonSQSExtendedClient sqsExtended;
  private final String queueUrl;
  private final int visibilityTimeout;
  public String queue = "test-queue";
  public String queueBucket = "test-bucket";

  /**
   * Initialized by Quarkus
   * Initializes all clients to be used all across the program.
   */
  @Inject
  public SqsService(S3Client s3Client, SqsClient sqsClient,
                    Logger logger) {

    this.logger = logger;
    this.logger.infof("QUEUE BUCKET: %s", queueBucket);

    ExtendedClientConfiguration extendedClientConfiguration = new ExtendedClientConfiguration()
        .withAlwaysThroughS3(true)
        .withPayloadSupportEnabled(s3Client, queueBucket, true);
    this.sqsExtended = new AmazonSQSExtendedClient(sqsClient, extendedClientConfiguration);

    GetQueueUrlRequest queueUrlRequest =
        GetQueueUrlRequest.builder()
            .queueName(queue)
            .build();

    this.logger.infof("Preparing to retrieve Queue url for queue [%s]",
        queue);
    // Since the only queue that we can retrieve messages from is the queue with this bucket
    // we can just retrieve the queue url immediately.
    GetQueueUrlResponse queueUrlResponse = this.sqsExtended.getQueueUrl(queueUrlRequest);
    this.queueUrl = queueUrlResponse.queueUrl();
    // Queue Url
    this.logger.infof("Queue url successfully retrieved : %s", this.queueUrl);
    this.visibilityTimeout = 10;
  }


  /**
   * Called by getMessage().
   * Gets a list containing the query from SQS. It also hides the selected message
   * for 10 seconds so that no other process can work with it. And limits how many
   * message are get to 1 per call.
   *
   * @return The list containing the query from SQS.
   */
  public List<Message> getQueueMessage() {
    ReceiveMessageRequest requestQueryMessage =
        ReceiveMessageRequest.builder()
            .visibilityTimeout(
                this.visibilityTimeout)//time to hide this queue from any other process
            .queueUrl(this.queueUrl)
            .maxNumberOfMessages(1)//messages at a time
            .build();
    return this.sqsExtended.receiveMessage(requestQueryMessage).messages();
  }

  /**
   * Deletes sqs queue.
   */
  public void deleteMessage(String messageHandle) {
    this.logger.infof("deleting message %s from %s", messageHandle, queueUrl);
    DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
        .queueUrl(queueUrl)
        .receiptHandle(messageHandle)
        .build();
    sqsExtended.deleteMessage(deleteMessageRequest);
  }


  /**
   * Send Message to queue.
   *
   * @param message - message to send to queue
   */
  public void sendMessage(String message) {

    SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(message)
        .build();
    this.sqsExtended.sendMessage(sendMessageRequest);
  }

  public String queueUrl() {
    return queueUrl;
  }
}
