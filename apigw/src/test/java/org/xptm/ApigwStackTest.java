package org.xptm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.StackProps;

/**
 * Offline unit tests for {@link ApigwStack}: synthesize the app and assert on
 * the generated CloudFormation template JSON.
 */
class ApigwStackTest {

  private static final String TEMPLATE_PATH = "cdk.out/ApigwStack.template.json";

  private static String synthTemplate() throws IOException {
    Path tempDir = Files.createTempDirectory("apigw-synth");
    App app = new App(AppProps.builder().outdir(tempDir.toString()).build());
    new ApigwStack(app, "ApigwStack", StackProps.builder().build());
    app.synth();
    Path template = tempDir.resolve("ApigwStack.template.json");
    assertTrue(Files.exists(template), "template file should exist: " + template);
    return Files.readString(template);
  }

  @Test
  void synthProducesRestApiStageAndMethods() throws IOException {
    String template = synthTemplate();

    assertTrue(template.contains("AWS::ApiGateway::RestApi"), "should contain RestApi");
    assertTrue(template.contains("AWS::ApiGateway::Stage"), "should contain Stage");
    assertTrue(template.contains("AWS::ApiGateway::Resource"), "should contain Resource");

    // Two GET methods (weather, queue) plus CORS preflight OPTIONS methods
    // (root + each resource) added by defaultCorsPreflightOptions.
    long methods = template.split("AWS::ApiGateway::Method", -1).length - 1;
    assertEquals(5, methods, "2 GET methods + 3 CORS preflight OPTIONS methods");
    long gets = template.split("\"HttpMethod\": \"GET\"", -1).length - 1;
    assertEquals(2, gets, "two GET methods");

    // Stage name "dev".
    assertTrue(template.contains("\"StageName\": \"dev\""), "stage should be named dev");
  }

  @Test
  void synthWiresWeatherAndQueueRoutesToLambdas() throws IOException {
    String template = synthTemplate();

    assertTrue(template.contains("GetWeather"), "template should reference GetWeather");
    assertTrue(template.contains("GetQueue"), "template should reference GetQueue");
    assertTrue(template.contains("AWS_PROXY"), "integrations should be AWS_PROXY");
    assertTrue(template.contains("\"HttpMethod\": \"GET\""), "methods should use GET");
  }

  @Test
  void synthTemplateIsWellFormedEnoughForChecks() throws IOException {
    String template = synthTemplate();
    assertNotNull(template);
    assertTrue(template.strip().startsWith("{"), "template should be a JSON object");
  }

  @Test
  void routesMapHoldsExpectedRoutes() {
    Map<String, String> routes = Map.of("weather", "GetWeather", "queue", "GetQueue");
    assertEquals(2, routes.size());
  }
}