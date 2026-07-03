import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';

export class EcsStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const vpc = new ec2.Vpc(this, 'Vpc', {
      maxAzs: 2,
    });

    const cluster = new ecs.Cluster(this, 'Cluster', {
      vpc,
    });

    const nginxService = new ecsPatterns.ApplicationLoadBalancedFargateService(this, 'NginxService', {
      cluster,
      desiredCount: 1,
      taskImageOptions: {
        image: ecs.ContainerImage.fromRegistry('nginx'),
        containerPort: 80,
      },
    });

    const vueService = new ecsPatterns.ApplicationLoadBalancedFargateService(this, 'VueService', {
      cluster,
      desiredCount: 1,
      taskImageOptions: {
        image: ecs.ContainerImage.fromRegistry('node:22-alpine'),
        containerPort: 8080,
      },
    });
  }
}