package com.example.ecs;

import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

/**
 * CDK app entry point for the ECS dev VM stack.
 *
 * Resolves the deployment environment from the process environment:
 * DEV_AWS_ACCOUNT / DEV_AWS_REGION first, then CDK_DEFAULT_ACCOUNT /
 * CDK_DEFAULT_REGION (set when a CDK CLI drives the app), and falls back to
 * the MiniStack defaults 000000000000 / us-east-1.
 */
public class EcsDevVmApp {
    public static void main(final String[] args) {
        String outdir = System.getenv().getOrDefault("CDK_OUTDIR", "cdk.out");
        App app = new App(AppProps.builder().outdir(outdir).build());

        new EcsDevVmStack(app, "EcsDevVmStack", StackProps.builder()
                .env(Environment.builder()
                        .account(resolveAccount())
                        .region(resolveRegion())
                        .build())
                .build());

        app.synth();
    }

    /**
     * Resolves the AWS account id: DEV_AWS_ACCOUNT, then CDK_DEFAULT_ACCOUNT,
     * then the MiniStack default 000000000000.
     */
    private static String resolveAccount() {
        return resolve("DEV_AWS_ACCOUNT", "CDK_DEFAULT_ACCOUNT", "000000000000");
    }

    /**
     * Resolves the AWS region: DEV_AWS_REGION, then CDK_DEFAULT_REGION, then
     * the MiniStack default us-east-1.
     */
    private static String resolveRegion() {
        return resolve("DEV_AWS_REGION", "CDK_DEFAULT_REGION", "us-east-1");
    }

    /**
     * Returns the first non-blank value among the environment variables, or
     * the given default when none is set.
     */
    private static String resolve(String... names) {
        for (String name : names) {
            String v = System.getenv(name);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return names[names.length - 1];
    }
}