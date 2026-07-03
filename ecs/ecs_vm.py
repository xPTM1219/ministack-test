import boto3
import sys

ecs = boto3.client("ecs", endpoint_url="http://localhost:4566",
                   aws_access_key_id="test", aws_secret_access_key="test", region_name="us-east-1")

cluster_name = "dev-xptm"
container_name = "al23-vm"
image = "public.ecr.aws/amazonlinux/amazonlinux:2023"
taskDefinition = "vms"

# ecs.create_cluster(clusterName=cluster_name)

task_def = {
    "family": taskDefinition,
    "containerDefinitions": [{
        "name": container_name,
        "image": "public.ecr.aws/amazonlinux/amazonlinux:2023",
        "cpu": 256,
        "memory": 512,
        "essential": True,
        "portMappings": [
            # Change this! 8080 is taken on your host.
            {"containerPort": 80, "hostPort": 8081}
        ],
        # Make it stay alive for testing (or replace with your real CMD)
        "command": ["bash", "-c"],
    }],
    "networkMode": "bridge",
    "requiresCompatibilities": ["EC2"],
}

ecs.register_task_definition(**task_def)

if sys.argv[1] == "start":
    # This actually runs an nginx container via Docker
    resp = ecs.run_task(cluster=cluster_name, taskDefinition=taskDefinition, count=1)
    task_arn = resp["tasks"][0]["taskArn"]
    print(task_arn)

if sys.argv[1] == "stop":
    # Stop it (removes the container)
    ecs.stop_task(cluster=cluster_name, task=task_arn)

