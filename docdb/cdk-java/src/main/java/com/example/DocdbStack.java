package com.example;

import software.amazon.awscdk.App;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.docdb.DatabaseCluster;
import software.amazon.awscdk.services.docdb.Login;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;

public class DocdbStack extends Stack {
    public DocdbStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);

        String version = System.getenv().getOrDefault("DOCDB_VERSION", "5.0.0");
        String instanceSpec = System.getenv().getOrDefault("DOCDB_INSTANCE", "db.r6g.large");

        // e.g. "db.r6g.large" -> InstanceClass R6G + InstanceSize LARGE
        String[] parts = instanceSpec.split("\\.");
        if (parts.length != 3 || !"db".equals(parts[0])) {
            throw new IllegalArgumentException(
                    "DOCDB_INSTANCE must look like 'db.<class>.<size>', got: " + instanceSpec);
        }
        InstanceClass instanceClass = InstanceClass.valueOf(parts[1].toUpperCase());
        InstanceSize instanceSize = InstanceSize.valueOf(parts[2].toUpperCase());

        Vpc vpc = Vpc.Builder.create(this, "Vpc").maxAzs(2).build();

        // Omitting the Login password makes the L2 generate one into
        // SecretsManager automatically and wire it via dynamic references.
        DatabaseCluster cluster = DatabaseCluster.Builder.create(this, "DocdbCluster")
                .masterUser(Login.builder()
                        .username("docdbadmin")
                        .build())
                .engineVersion(version)
                .instanceType(InstanceType.of(instanceClass, instanceSize))
                .vpc(vpc)
                .instances(1)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        CfnOutput.Builder.create(this, "DocdbClusterId")
                .value(cluster.getClusterIdentifier())
                .build();
        CfnOutput.Builder.create(this, "DocdbClusterEndpoint")
                .value(cluster.getClusterEndpoint().getHostname())
                .build();
        CfnOutput.Builder.create(this, "DocdbClusterPort")
                .value(cluster.getClusterEndpoint().getPort().toString())
                .build();
    }
}
