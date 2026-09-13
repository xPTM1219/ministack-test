package com.example.ecs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcAttributes;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinition;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.EfsVolumeConfiguration;
import software.amazon.awscdk.services.ecs.AuthorizationConfig;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.Host;
import software.amazon.awscdk.services.ecs.LinuxParameters;
import software.amazon.awscdk.services.ecs.LinuxParametersProps;
import software.amazon.awscdk.services.ecs.LoadBalancerTargetOptions;
import software.amazon.awscdk.services.ecs.MountPoint;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.Secret;
import software.amazon.awscdk.services.ecs.Volume;
import software.amazon.awscdk.services.efs.AccessPoint;
import software.amazon.awscdk.services.efs.AccessPointOptions;
import software.amazon.awscdk.services.efs.Acl;
import software.amazon.awscdk.services.efs.FileSystem;
import software.amazon.awscdk.services.efs.LifecyclePolicy;
import software.amazon.awscdk.services.efs.PerformanceMode;
import software.amazon.awscdk.services.efs.PosixUser;
import software.amazon.awscdk.services.efs.ThroughputMode;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseNetworkListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.ssm.StringParameter;

/**
 * ECS Fargate dev VM ("devbox") stack.
 *
 * MINISTACK mode (MINISTACK=1): no VPC/NAT/EIP/EFS/NLB resources are emitted.
 * The VPC is imported by id, /home/dev persists on a host bind mount, and SSH
 * targets the task's private IP directly. The synthesized template contains
 * only resource types accepted by MiniStack's CFN provisioner whitelist.
 *
 * AWS mode: adds EFS (mount targets + access point, uid 1000) and an
 * internet-facing NLB (optional EIP allocation ids) for a stable SSH target.
 */
public class EcsDevVmStack extends Stack {

    public EcsDevVmStack(final software.constructs.Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        final boolean ministack = isTruthy(env("MINISTACK", "0"));
        final String user = env("DEV_USER", "xptm");
        final String image = env("DEV_IMAGE", "ecs-dev-vm:" + user);
        final int cpu = Integer.parseInt(env("DEV_TASK_CPU", "4096"));
        final int mem = Integer.parseInt(env("DEV_TASK_MEM", "16384"));
        final String sshPublicKey = env("DEV_SSH_PUBLIC_KEY", "");
        if (sshPublicKey.isEmpty()) {
            throw new IllegalArgumentException(
                    "DEV_SSH_PUBLIC_KEY is required (contents of an ssh public key file)");
        }

        // ------------------------------------------------------------------
        // 1. SSH public key in SSM Parameter Store.
        //    Plain String (not SecureString): MiniStack's CFN SSM provisioner
        //    accepts only String/StringList, and this is a public key anyway.
        //    On AWS the container also reads it via the `secrets` block at
        //    task start; on MiniStack CFN-created task-def `secrets` entries
        //    are not resolved by the ECS secret resolver, so the key flows as
        //    a plain container environment variable there instead.
        // ------------------------------------------------------------------
        StringParameter keyParam = StringParameter.Builder.create(this, "SshPublicKeyParameter")
                .parameterName("/devboxes/" + user + "/ssh-public-key")
                .stringValue(sshPublicKey)
                .build();

        // ------------------------------------------------------------------
        // 2. Imported VPC / subnets / security groups
        // ------------------------------------------------------------------
        final String vpcId = env("DEV_VPC_ID", "");
        final String subnetIds = env("DEV_SUBNET_IDS", "");
        final String publicSubnetIds = env("DEV_PUBLIC_SUBNET_IDS", "");
        final String sgIds = env("DEV_SG_IDS", "");
        if (vpcId.isEmpty() || subnetIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "DEV_VPC_ID and DEV_SUBNET_IDS are required (comma-separated lists allowed)");
        }
        List<String> subnets = csv(subnetIds);
        List<String> azs = new ArrayList<>();
        for (int i = 0; i < subnets.size(); i++) {
            azs.add("us-east-1" + (char) ('a' + (i % 26)));
        }

        IVpc vpc = Vpc.fromVpcAttributes(this, "ImportedVpc", VpcAttributes.builder()
                .vpcId(vpcId)
                .availabilityZones(azs)
                .privateSubnetIds(subnets)
                .publicSubnetIds(publicSubnetIds.isEmpty() ? null : csv(publicSubnetIds))
                .build());

        List<String> extraSgIds = csv(sgIds);

        // Task-local security group. The SSH ingress rule is rendered inline
        // on the SG (CidrIp/FromPort/ToPort only) — exactly what MiniStack's
        // provisioner parses; standalone AWS::EC2::SecurityGroupIngress
        // resources are NOT accepted by MiniStack pre-flight.
        SecurityGroup taskSg = SecurityGroup.Builder.create(this, "DevVmSg")
                .vpc(vpc)
                .description("devvm ssh access")
                .build();
        taskSg.addIngressRule(Peer.ipv4("0.0.0.0/0"), Port.tcp(22),
                "SSH from anywhere (tighten for real deployments)");

        List<ISecurityGroup> serviceSgs = new ArrayList<>();
        serviceSgs.add(taskSg);
        int sgIdx = 0;
        for (String sgId : extraSgIds) {
            serviceSgs.add(SecurityGroup.fromSecurityGroupId(
                    this, "ImportedSg" + sgIdx++, sgId));
        }

        // ------------------------------------------------------------------
        // 3. ECS cluster
        // ------------------------------------------------------------------
        Cluster cluster = Cluster.Builder.create(this, "DevCluster")
                .clusterName("dev-" + user)
                .vpc(vpc)
                .build();

        // ------------------------------------------------------------------
        // 4. Task definition (FARGATE, awsvpc). No runtimePlatform: the
        //    task definition defaults to the host architecture on MiniStack,
        //    avoiding a pointless platform pin. The execution role is created
        //    eagerly (the default one only materializes when a feature that
        //    needs it is used, which made getExecutionRole() null in AWS mode).
        // ------------------------------------------------------------------
        IRole executionRole = Role.Builder.create(this, "DevVmExecutionRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .build();
        FargateTaskDefinition td = FargateTaskDefinition.Builder.create(this, "DevVmTaskDef")
                .family("devvm-" + user)
                .cpu(cpu)
                .memoryLimitMiB(mem)
                .executionRole(executionRole)
                .build();

        // break-glass parameter read for the task role
        td.getTaskRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("ssm:GetParameter"))
                .resources(List.of(keyParam.getParameterArn()))
                .build());

        // ------------------------------------------------------------------
        // 5. dev-home volume: host bind (MiniStack) or EFS + access point (AWS)
        // ------------------------------------------------------------------
        if (ministack) {
            String dataDir = env("DEV_DATA_DIR", "");
            if (dataDir.isEmpty()) {
                throw new IllegalArgumentException(
                        "MINISTACK mode requires DEV_DATA_DIR: an absolute host path for the /home/dev bind mount");
            }
            td.addVolume(Volume.builder()
                    .name("dev-home")
                    .host(Host.builder().sourcePath(dataDir).build())
                    .build());
        } else {
            FileSystem efs = FileSystem.Builder.create(this, "DevHomeEfs")
                    .vpc(vpc)
                    .removalPolicy(RemovalPolicy.RETAIN)
                    .performanceMode(PerformanceMode.GENERAL_PURPOSE)
                    .throughputMode(ThroughputMode.BURSTING)
                    .lifecyclePolicy(LifecyclePolicy.AFTER_7_DAYS)
                    .build();
            AccessPoint ap = efs.addAccessPoint("DevHomeAccessPoint", AccessPointOptions.builder()
                    .path("/" + user)
                    .posixUser(PosixUser.builder().uid("1000").gid("1000").build())
                    .createAcl(Acl.builder().ownerUid("1000").ownerGid("1000").permissions("0755").build())
                    .build());
            td.addVolume(Volume.builder()
                    .name("dev-home")
                    .efsVolumeConfiguration(EfsVolumeConfiguration.builder()
                            .fileSystemId(efs.getFileSystemId())
                            .authorizationConfig(AuthorizationConfig.builder()
                                    .accessPointId(ap.getAccessPointId())
                                    .iam("ENABLED")
                                    .build())
                            .transitEncryption("ENABLED")
                            .build())
                    .build());
            // EFS access-point authorization requires the *execution* role to
            // mount; grant on the explicit execution role.
            executionRole.addToPrincipalPolicy(PolicyStatement.Builder.create()
                    .effect(Effect.ALLOW)
                    .actions(List.of(
                            "elasticfilesystem:ClientMount",
                            "elasticfilesystem:ClientRootAccess",
                            "elasticfilesystem:ClientWrite"))
                    .resources(List.of(efs.getFileSystemArn()))
                    .build());
            keyParam.grantRead(executionRole);
        }

        // ------------------------------------------------------------------
        // 6. devvm container: sshd + XFCE + VSCode + rootless docker
        // ------------------------------------------------------------------
        LinuxParameters linuxParams = new LinuxParameters(this, "DevVmLinuxParams",
                LinuxParametersProps.builder().initProcessEnabled(true).build());
        // MiniStack forwards container-level `privileged` to docker run; the
        // nested (DinD) dockerd needs real MS_BIND/MS_SETFLAGS support, which
        // the host security module only grants to privileged containers.
        // Fargate forbids privileged — this stays MiniStack-only.
        boolean privileged = ministack;
        ContainerDefinitionOptions containerOpts = ContainerDefinitionOptions.builder()
                .containerName("devvm")
                .image(ContainerImage.fromRegistry(image))
                .portMappings(List.of(PortMapping.builder()
                        .containerPort(22)
                        .hostPort(22)
                        .protocol(Protocol.TCP)
                        .build()))
                .environment(ministack
                        ? Map.of(
                                "DEV_USER", user,
                                "DEV_SSH_PUBLIC_KEY", sshPublicKey,
                                // rootlesskit cannot run inside a container
                                // whose mount ns is owned by the initial
                                // user namespace (plain outer Docker) —
                                // MiniStack tasks run a nested root dockerd
                                // (DinD) instead; Fargate keeps rootless.
                                "DEV_DOCKER_MODE", "root")
                        : Map.of(
                                "DEV_USER", user,
                                "DEV_SSH_PUBLIC_KEY", sshPublicKey))
                .linuxParameters(linuxParams)
                .privileged(privileged)
                .build();

        ContainerDefinition devvm = td.addContainer("devvm", containerOpts);

        if (!ministack) {
            // AWS: prefer SSM at task start; env var remains as bootstrap copy
            devvm.addSecret("DEV_SSH_PUBLIC_KEY", Secret.fromSsmParameter(
                    software.amazon.awscdk.services.ssm.StringParameter.fromStringParameterAttributes(
                            this, "SshKeyRef",
                            software.amazon.awscdk.services.ssm.StringParameterAttributes.builder()
                                    .parameterName(keyParam.getParameterName())
                                    .simpleName(false)
                                    .build())));
        }

        devvm.addMountPoints(MountPoint.builder()
                .sourceVolume("dev-home")
                .containerPath("/home/" + user)
                .readOnly(false)
                .build());

        // ------------------------------------------------------------------
        // 7. Fargate service
        // ------------------------------------------------------------------
        FargateService service = FargateService.Builder.create(this, "DevVmService")
                .cluster(cluster)
                .serviceName("devvm-" + user)
                .taskDefinition(td)
                .desiredCount(1)
                .assignPublicIp(false)
                .vpcSubnets(SubnetSelection.builder().subnets(vpc.getPrivateSubnets()).build())
                .securityGroups(serviceSgs)
                .enableExecuteCommand(true)
                .build();

        // ------------------------------------------------------------------
        // 8. AWS-only: internet-facing NLB -> stable SSH target
        // ------------------------------------------------------------------
        if (!ministack) {
            String eipAllocs = env("DEV_EIP_ALLOCS", "");
            NetworkLoadBalancer.Builder nlbBuilder = NetworkLoadBalancer.Builder.create(this, "DevVmNlb")
                    .vpc(vpc)
                    .internetFacing(true)
                    .vpcSubnets(SubnetSelection.builder().subnets(vpc.getPublicSubnets()).build());
            if (!eipAllocs.isEmpty()) {
                List<String> allocs = csv(eipAllocs);
                List<software.amazon.awscdk.services.elasticloadbalancingv2.SubnetMapping> mappings =
                        new ArrayList<>();
                List<software.amazon.awscdk.services.ec2.ISubnet> pubSubnets = vpc.getPublicSubnets();
                for (int i = 0; i < allocs.size(); i++) {
                    mappings.add(software.amazon.awscdk.services.elasticloadbalancingv2.SubnetMapping.builder()
                            .subnet(pubSubnets.get(i % pubSubnets.size()))
                            .allocationId(allocs.get(i))
                            .build());
                }
                nlbBuilder.subnetMappings(mappings);
            }
            NetworkLoadBalancer nlb = nlbBuilder.build();

            NetworkTargetGroup tg = NetworkTargetGroup.Builder.create(this, "DevVmTg")
                    .vpc(vpc)
                    .port(22)
                    .targetType(TargetType.IP)
                    .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP)
                    .healthCheck(HealthCheck.builder()
                            .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP)
                            .port("22")
                            .interval(Duration.seconds(30))
                            .build())
                    .build();

            nlb.addListener("DevVmSshListener", BaseNetworkListenerProps.builder()
                    .port(22)
                    .defaultTargetGroups(List.of(tg))
                    .build());

            tg.addTarget(service.loadBalancerTarget(LoadBalancerTargetOptions.builder()
                    .containerName("devvm")
                    .containerPort(22)
                    .build()));

            CfnOutput.Builder.create(this, "NlbDnsName").value(nlb.getLoadBalancerDnsName()).build();
        }

        // ------------------------------------------------------------------
        // 9. Outputs
        // ------------------------------------------------------------------
        CfnOutput.Builder.create(this, "ClusterName").value(cluster.getClusterName()).build();
        CfnOutput.Builder.create(this, "ServiceName").value(service.getServiceName()).build();
        CfnOutput.Builder.create(this, "SshKeyParameter").value(keyParam.getParameterName()).build();
        CfnOutput.Builder.create(this, "DevVmFamily").value("devvm-" + user).build();
        CfnOutput.Builder.create(this, "SshHint")
                .value("SSH target = task private IP: aws ecs describe-tasks --cluster "
                        + cluster.getClusterName() + " --task <arn> --query "
                        + "'tasks[0].attachments[0].details[?name==`privateIPv4Address`].value' --output text")
                .build();
    }

    private static boolean isTruthy(String s) {
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return v == null ? def : v.trim();
    }

    private static List<String> csv(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (!part.trim().isEmpty()) {
                out.add(part.trim());
            }
        }
        return out;
    }
}