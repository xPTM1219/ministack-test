package xptm.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

/**
 * Test class for BasicSqsService
 */
@QuarkusTest
class BasicSqsServiceTest {

    @InjectMock
    SqsClient mockSqsClient;

    @Inject
    BasicSqsService basicSqsService;

    private static final String QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
    private static final String QUEUE_NAME = "test-queue";
    private static final int VISIBILITY_TIMEOUT = 30;

    @BeforeEach
    void setUp() {
        // Mock GetQueueUrlResponse
        GetQueueUrlResponse queueUrlResponse = GetQueueUrlResponse.builder()
                .queueUrl(QUEUE_URL)
                .build();
        when(mockSqsClient.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(queueUrlResponse);
    }

    @Test
    void testReceiveMessages() {
        // Mock ReceiveMessageResponse with empty messages
        ReceiveMessageResponse receiveResponse = ReceiveMessageResponse.builder()
                .messages(Collections.emptyList())
                .build();
        when(mockSqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResponse);

        List<Message> messages = basicSqsService.receiveMessages();

        assertNotNull(messages);
        assertEquals(0, messages.size());
        verify(mockSqsClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void testSendMessage() {
        // Mock SendMessageResponse
        SendMessageResponse sendResponse = SendMessageResponse.builder()
                .messageId("test-message-id")
                .build();
        when(mockSqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(sendResponse);

        String testMessage = "Test message body";
        basicSqsService.sendMessage(testMessage);

        verify(mockSqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testDeleteMessage() {
        // Mock DeleteMessageResponse
        DeleteMessageResponse deleteResponse = DeleteMessageResponse.builder().build();
        when(mockSqsClient.deleteMessage(any(DeleteMessageRequest.class))).thenReturn(deleteResponse);

        String receiptHandle = "test-receipt-handle";
        basicSqsService.deleteMessage(receiptHandle);

        verify(mockSqsClient, times(1)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void testGetQueueUrl() {
        String queueUrl = basicSqsService.getQueueUrl();

        assertEquals(QUEUE_URL, queueUrl);
    }
}