package com.example;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

public class EcsApp {
    public static void main(final String[] args) {
        App app = new App();

        new EcsStack(app, "EcsStack", StackProps.builder().build());

        app.synth();
    }
}