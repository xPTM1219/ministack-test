import os

from aws_cdk import (
    CfnOutput,
    RemovalPolicy,
    Stack,
)
from aws_cdk import aws_ec2 as ec2
from aws_cdk import aws_docdb as docdb


class DocdbStack(Stack):
    def __init__(self, scope, construct_id, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        version = os.environ.get("DOCDB_VERSION", "5.0.0")
        instance_spec = os.environ.get("DOCDB_INSTANCE", "db.r6g.large")

        # e.g. "db.r6g.large" -> InstanceClass R6G + InstanceSize LARGE
        parts = instance_spec.split(".")
        if len(parts) != 3 or parts[0] != "db":
            raise ValueError(
                f"DOCDB_INSTANCE must look like 'db.<class>.<size>', got: {instance_spec}"
            )
        instance_class = getattr(ec2.InstanceClass, parts[1].upper())
        instance_size = getattr(ec2.InstanceSize, parts[2].upper())

        vpc = ec2.Vpc(self, "Vpc", max_azs=2)

        # Omitting master_user password makes the L2 generate one into
        # SecretsManager automatically and wire it via dynamic references.
        cluster = docdb.DatabaseCluster(
            self,
            "DocdbCluster",
            master_user=docdb.Login(username="docdbadmin"),
            engine_version=version,
            instance_type=ec2.InstanceType.of(instance_class, instance_size),
            vpc=vpc,
            instances=1,
            removal_policy=RemovalPolicy.DESTROY,
        )

        CfnOutput(self, "DocdbClusterId", value=cluster.cluster_identifier)
        CfnOutput(self, "DocdbClusterEndpoint", value=cluster.cluster_endpoint.hostname)
        CfnOutput(self, "DocdbClusterPort", value=str(cluster.cluster_endpoint.port))
