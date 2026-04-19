package com.example;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

public class K3sApp {
    public static void main(final String[] args) {
        App app = new App();

        new K3sStack(app, "K3sStack", StackProps.builder().build());

        app.synth();
    }
}