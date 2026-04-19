package com.example;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;

public class EcsStack extends Stack {
    public EcsStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = Vpc.Builder.create(this, "Vpc").maxAzs(2).build();

        Cluster cluster = Cluster.Builder.create(this, "Cluster")
                .vpc(vpc)
                .build();

        ApplicationLoadBalancedFargateService nginxService = ApplicationLoadBalancedFargateService.Builder.create(this, "NginxService")
                .cluster(cluster)
                .desiredCount(1)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .image(ContainerImage.fromRegistry("nginx"))
                        .containerPort(80)
                        .build())
                .build();

        ApplicationLoadBalancedFargateService vueService = ApplicationLoadBalancedFargateService.Builder.create(this, "VueService")
                .cluster(cluster)
                .desiredCount(1)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .image(ContainerImage.fromRegistry("node:16-alpine"))
                        .containerPort(8080)
                        .build())
                .build();
    }
}