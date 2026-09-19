package org.xptm;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * GetQueue lambda: returns SQS messages from a queue as JSON.
 *
 * <p>Receives messages (without deleting them) from the configured SQS queue
 * and returns them as an API Gateway proxy response. Message bodies are
 * parsed as JSON when possible, otherwise returned as raw strings.
 *
 * <p>Env vars: {@code SQS_ENDPOINT_URL} (default
 * {@code http://host.docker.internal:4566}), {@code QUEUE_URL} (default
 * test-queue URL), {@code MAX_MESSAGES} (default 50).
 */
public class GetQueueHandler
    implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  /** Default SQS endpoint inside the ministack container network. */
  static final String DEFAULT_ENDPOINT = "http://host.docker.internal:4566";

  /** Default queue URL for the ministack test queue. */
  static final String DEFAULT_QUEUE_URL =
      "http://sqs.us-east-1.localhost.ministack.cloud:4566/000000000000/test-queue";

  /** Default maximum number of messages returned per invocation. */
  static final int DEFAULT_MAX_MESSAGES = 50;

  /** SQS page size imposed by the ReceiveMessage API. */
  private static final int RECEIVE_PAGE_SIZE = 10;

  /** SQS endpoint URL to read messages from. */
  private final transient String endpointUrl;

  /** Full URL of the queue to read. */
  private final transient String queueUrl;

  /** Maximum number of messages to return. */
  private final transient int maxMessages;

  /** SQS client created lazily on first use. */
  private final transient SqsClient sqs;

  /**
   * Creates a handler configured from the {@code SQS_ENDPOINT_URL},
   * {@code QUEUE_URL} and {@code MAX_MESSAGES} environment variables.
   */
  public GetQueueHandler() {
    this.endpointUrl = envOrDefault("SQS_ENDPOINT_URL", DEFAULT_ENDPOINT);
    this.queueUrl = envOrDefault("QUEUE_URL", DEFAULT_QUEUE_URL);
    this.maxMessages = parseMaxMessages(System.getenv("MAX_MESSAGES"));
    this.sqs = makeClient(endpointUrl);
  }

  /**
   * Creates a handler with explicit settings, mainly for tests.
   *
   * @param endpointUrl SQS endpoint URL to read messages from
   * @param queueUrl full URL of the queue to read
   * @param maxMessages maximum number of messages to return
   */
  GetQueueHandler(final String endpointUrl, final String queueUrl, final int maxMessages) {
    this.endpointUrl = endpointUrl;
    this.queueUrl = queueUrl;
    this.maxMessages = maxMessages;
    this.sqs = makeClient(endpointUrl);
  }

  /**
   * Lambda entry point: returns the current SQS queue messages as JSON.
   *
   * @param input API Gateway proxy request (the route is GET /queue)
   * @param context Lambda context (unused)
   * @return API Gateway proxy response with the queue messages
   */
  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      final APIGatewayProxyRequestEvent input, final Context context) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Access-Control-Allow-Origin", "*");
    try {
      List<Map<String, Object>> messages = receiveMessages(sqs, queueUrl, maxMessages);
      String body = MAPPER.writeValueAsString(Map.of("messages", messages));
      return response(200, headers, body);
    } catch (Exception e) {
      String body = errorBody(e);
      return response(500, headers, body);
    }
  }

  /**
   * Receives up to {@code maxMessages} messages without deleting them.
   *
   * @param client SQS client to read with
   * @param url full URL of the queue to read
   * @param limit maximum number of messages to return
   * @return list of {@code {messageId, body, receiptHandle}} message maps
   */
  static List<Map<String, Object>> receiveMessages(
      final SqsClient client, final String url, final int limit) {
    List<Map<String, Object>> result = new ArrayList<>();
    while (result.size() < limit) {
      ReceiveMessageRequest request = ReceiveMessageRequest.builder()
          .queueUrl(url)
          .attributeNamesWithStrings("All")
          .messageAttributeNames(".*")
          .maxNumberOfMessages(Math.min(RECEIVE_PAGE_SIZE, limit - result.size()))
          .visibilityTimeout(1)
          .build();
      List<Message> batch = client.receiveMessage(request).messages();
      if (batch.isEmpty()) {
        break;
      }
      batch.forEach(message -> result.add(serializeMessage(message)));
    }
    return result;
  }

  /**
   * Maps a raw SQS message to a JSON-friendly map with a parsed body.
   *
   * @param message raw SQS message received from the queue
   * @return {@code {messageId, body, receiptHandle}} map; {@code body} is a
   *     JSON value when parseable, else the raw string
   */
  static Map<String, Object> serializeMessage(final Message message) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("messageId", message.messageId());
    item.put("body", parseBody(message.body()));
    item.put("receiptHandle", message.receiptHandle());
    return item;
  }

  /**
   * Parses a body string as JSON when possible, else returns the raw string.
   *
   * @param raw raw SQS message body
   * @return parsed JSON value, else the raw string
   */
  static Object parseBody(final String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    try {
      JsonNode node = MAPPER.readTree(raw);
      if (node == null || node.isMissingNode() || node.isTextual()) {
        return raw;
      }
      return MAPPER.treeToValue(node, Object.class);
    } catch (Exception e) {
      return raw;
    }
  }

  /**
   * Serializes the 500 response error payload.
   *
   * @param e exception that failed the request
   * @return JSON body with the error message
   */
  static String errorBody(final Exception e) {
    try {
      return MAPPER.writeValueAsString(Map.of("error", String.valueOf(e)));
    } catch (Exception inner) {
      return "{\"error\":\"internal error\"}";
    }
  }

  /**
   * Builds a proxy response with the given status, headers and body.
   *
   * @param status HTTP status code
   * @param headers response headers
   * @param body JSON body string
   * @return the proxy response
   */
  static APIGatewayProxyResponseEvent response(
      final int status, final Map<String, String> headers, final String body) {
    return new APIGatewayProxyResponseEvent()
        .withStatusCode(status)
        .withHeaders(headers)
        .withBody(body);
  }

  /**
   * Builds the SQS client pointed at the configured endpoint.
   *
   * @param endpoint SQS endpoint URL to point the client at
   * @return a ready-to-use SQS client
   */
  static SqsClient makeClient(final String endpoint) {
    return SqsClient.builder()
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create("test", "test")))
        .endpointOverride(URI.create(endpoint))
        .build();
  }

  /**
   * Reads an environment variable with a default fallback.
   *
   * @param name environment variable name
   * @param fallback value used when the variable is unset or blank
   * @return the variable value or the fallback
   */
  static String envOrDefault(final String name, final String fallback) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value;
  }

  /**
   * Parses the {@code MAX_MESSAGES} environment variable safely.
   *
   * @param raw raw environment variable value, may be null
   * @return parsed positive value or {@link #DEFAULT_MAX_MESSAGES}
   */
  static int parseMaxMessages(final String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_MAX_MESSAGES;
    }
    try {
      int parsed = Integer.parseInt(raw.trim());
      return Math.max(1, parsed);
    } catch (NumberFormatException e) {
      return DEFAULT_MAX_MESSAGES;
    }
  }

  /** Shared Jackson mapper for JSON parsing and serialization. */
  private static final ObjectMapper MAPPER = new ObjectMapper();
}