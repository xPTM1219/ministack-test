package org.xptm;

import java.util.List;
import java.util.Map;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.Cors;
import software.amazon.awscdk.services.apigateway.CorsOptions;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.StageOptions;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.IFunction;
import software.constructs.Construct;

/**
 * Weather API Gateway stack.
 *
 * Creates a REST API ("WeatherApi") with a {@code dev} stage and wires
 * {@code GET /weather} and {@code GET /queue} to the pre-existing
 * containerized lambdas {@code GetWeather} and {@code GetQueue} created by
 * CLI commands (see README.md). The lambdas are imported by name, so synth
 * works offline without any lookup.
 */
public class ApigwStack extends Stack {

  /** Route paths paired with the lambda names behind them. */
  private static final List<String[]> ROUTES = List.of(
      new String[] {"weather", "GetWeather"},
      new String[] {"queue", "GetQueue"});

  /** The REST API created by this stack. */
  private final RestApi api;

  /**
   * Synthesizes the WeatherApi REST API with routes to the two lambdas.
   *
   * @param scope parent construct (the CDK App)
   * @param id construct id, "ApigwStack"
   * @param props stack properties (env pinning)
   */
  public ApigwStack(final Construct scope, final String id, final StackProps props) {
    super(scope, id, props);

    this.api = makeApi();
    ROUTES.forEach(route -> attachRoute(api, route[0], route[1]));

    new CfnOutput(this, "ApiId", CfnOutputProps.builder().value(api.getRestApiId()).build());
    new CfnOutput(this, "ApiUrl", CfnOutputProps.builder().value(api.getUrl()).build());
  }

  /**
   * Creates the REST API with a dev stage and permissive CORS preflight.
   *
   * @return the created RestApi
   */
  RestApi makeApi() {
    return RestApi.Builder.create(this, "WeatherApi")
        .restApiName("WeatherApi")
        .deploy(true)
        .deployOptions(StageOptions.builder().stageName("dev").build())
        .defaultCorsPreflightOptions(CorsOptions.builder()
            .allowOrigins(Cors.ALL_ORIGINS)
            .allowMethods(Cors.ALL_METHODS)
            .build())
        .build();
  }

  /**
   * Attaches a GET route integrated with a pre-existing lambda.
   *
   * @param restApi the REST API to attach the route to
   * @param path resource path below the root, e.g. "weather"
   * @param functionName pre-existing lambda name, e.g. "GetWeather"
   */
  void attachRoute(final RestApi restApi, final String path, final String functionName) {
    IFunction lambdaFunction = Function.fromFunctionName(this, functionName, functionName);
    LambdaIntegration integration = new LambdaIntegration(lambdaFunction);
    restApi.getRoot().resourceForPath(path).addMethod("GET", integration);
  }
}