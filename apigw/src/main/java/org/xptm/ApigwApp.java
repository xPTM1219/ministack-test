package org.xptm;

import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

/**
 * CDK app entry point for the Weather API Gateway stack.
 *
 * Synthesizes {@link ApigwStack} into {@code cdk.out/} (override with the
 * {@code CDK_OUTDIR} env var) when run via {@code mvn compile exec:java}.
 */
public class ApigwApp {

  /** Ministack account id used for environment pinning. */
  private static final String ACCOUNT = "000000000000";

  /** Region used for all ministack deployments. */
  private static final String REGION = "us-east-1";

  /**
   * Builds the CDK app and synthesizes the stack to the output directory.
   *
   * @param args unused CLI arguments
   */
  public static void main(final String[] args) {
    String outdir = System.getenv().getOrDefault("CDK_OUTDIR", "cdk.out");
    App app = new App(AppProps.builder().outdir(outdir).build());

    new ApigwStack(app, "ApigwStack", StackProps.builder()
        .env(Environment.builder().account(ACCOUNT).region(REGION).build())
        .build());

    app.synth();
  }

  private ApigwApp() {
    // utility class
  }
}