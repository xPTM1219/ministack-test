package org.xptm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

/**
 * Smoke test: the CDK app wiring constructs without exceptions.
 */
class ApigwAppTest {

  @Test
  void stackConstructsWithoutException() {
    assertDoesNotThrow(() -> {
      App app = new App();
      new ApigwStack(app, "ApigwStackSmoke", StackProps.builder().build());
    });
  }
}