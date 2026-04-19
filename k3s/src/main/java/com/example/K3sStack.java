package com.example;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.KubernetesVersion;
import software.amazon.awscdk.services.eks.NodegroupAmiType;
import software.amazon.awscdk.services.eks.NodegroupOptions;

public class K3sStack extends Stack {
    public K3sStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = Vpc.Builder.create(this, "Vpc").maxAzs(2).build();

        Cluster cluster = Cluster.Builder.create(this, "EksCluster")
                .vpc(vpc)
                .version(KubernetesVersion.V1_27)
                .build();

        // Node group for Apache2 (using AL2 AMI)
        cluster.addNodegroupCapacity("Apache2NodeGroup", NodegroupOptions.builder()
                .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.SMALL))
                .minSize(1)
                .maxSize(3)
                .desiredSize(1)
                .amiType(NodegroupAmiType.AL2_X86_64)
                .build());

        // Node group for Alpine (using AL2 AMI as Alpine not directly supported)
        cluster.addNodegroupCapacity("AlpineNodeGroup", NodegroupOptions.builder()
                .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.SMALL))
                .minSize(1)
                .maxSize(3)
                .desiredSize(1)
                .amiType(NodegroupAmiType.AL2_X86_64)
                .build());
    }
}