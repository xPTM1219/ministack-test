package com.example.ecs;

import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class EcsDevVmApp {
    public static void main(final String[] args) {
        String outdir = System.getenv().getOrDefault("CDK_OUTDIR", "cdk.out");
        App app = new App(AppProps.builder().outdir(outdir).build());

        new EcsDevVmStack(app, "EcsDevVmStack", StackProps.builder()
                .env(Environment.builder()
                        .account("000000000000")
                        .region("us-east-1")
                        .build())
                .build());

        app.synth();
    }
}