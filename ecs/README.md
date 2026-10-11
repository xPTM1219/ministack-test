# ECS Fargate Dev VM ("devvm")

A per-user development VM that runs as an **ECS Fargate task**: Rocky 9 + sshd
(pubkey auth) + XFCE + VS Code + Node.js + Kilo Code CLI + Termide + Herdr +
a nested Docker daemon, with the home directory persisted across task
restarts. The same CDK stack deploys to MiniStack (local) and real AWS.

```
ecs/
├── Dockerfile            Rocky 9 image: sshd, XFCE, VSCode, Node, kilo,
│                         termide, herdr, Docker CE (pinned checksums)
├── image/
│   ├── supervisord.conf  PID-1 config (sshd + dockerd; rendered per-mode)
│   ├── entrypoint.sh     installs SSH key, prepares runtime, renders config
│   └── 99-devbox.sh      profile: DOCKER_HOST for the nested daemon
├── build-image.sh        builds ecs-dev-vm:<user> with the host docker
├── deploy-aws.sh         ECR push + AWS-mode synth + cloudformation deploy
├── cdk/                  Java CDK app (pom.xml, EcsDevVmApp, EcsDevVmStack)
│   └── cdk.out/          synthesized templates (MiniStack + AWS modes)
├── ecs_vm.py             ops CLI: status/ssh/start/stop/restart/logs/exec
└── test_ssh.sh           automated SSH acceptance test
```

## Quick start (MiniStack)

```bash
# 0. one-time: CDK bootstrap shim + a bind dir for /home/<user>
aws ssm put-parameter --endpoint-url http://localhost:4566 \
    --name /cdk-bootstrap/hnb659fds/version --type String --value 6
mkdir -p /tmp/kilo/ecs-dev-<user>          # host path for the home bind mount

# 1. build the dev-vm image (host docker; MiniStack's ECS reuses it)
bash ecs/build-image.sh <user>

# 2. synthesize + deploy (MINISTACK=1 selects bind-mount mode)
cd ecs/cdk
env "DEV_SSH_PUBLIC_KEY=$(cat ~/.ssh/id_rsa.pub)" \
    MINISTACK=1 DEV_USER=<user> DEV_IMAGE=ecs-dev-vm:<user> \
    DEV_VPC_ID=vpc-00000001 \
    DEV_SUBNET_IDS=subnet-00000001,subnet-00000002,subnet-00000003 \
    DEV_DATA_DIR=/tmp/kilo/ecs-dev-<user> \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    mvn -q compile exec:java

aws --endpoint-url http://localhost:4566 cloudformation deploy \
    --template-file cdk.out/EcsDevVmStack.template.json \
    --stack-name ecs-dev-<user> --capabilities CAPABILITY_IAM

# 3. operate
../../venv/bin/python ecs_vm.py status      # task IP, ssh command
../../venv/bin/python ecs_vm.py ssh         # interactive SSH session
../../venv/bin/python ecs_vm.py logs        # devvm container logs
../../venv/bin/python ecs_vm.py exec        # docker exec break-glass
../../venv/bin/python ecs_vm.py restart     # recycle the task (home persists)

# 4. acceptance
bash ecs/test_ssh.sh <task-ip> ~/.ssh/id_rsa <user>
```

Env vars used by the stack (all passed at synth time):

| Variable | Meaning | MiniStack default |
|---|---|---|
| `MINISTACK` | `1` = bind-mount mode, no EFS/NLB | unset → AWS mode |
| `DEV_USER` | username / home owner | `xptm` |
| `DEV_IMAGE` | image ref; an ECR URI gets an automatic pull grant on the execution role | `ecs-dev-vm:<user>` |
| `DEV_SSH_PUBLIC_KEY` | pubkey contents (required) | — |
| `DEV_VPC_ID` / `DEV_SUBNET_IDS` | imported VPC + private subnets | — |
| `DEV_PUBLIC_SUBNET_IDS` | public subnets for the NLB (AWS mode, required) | — |
| `DEV_SG_IDS` | extra security groups | — |
| `DEV_DATA_DIR` | absolute host path for `/home/<user>` bind | required in MiniStack |
| `DEV_TASK_CPU` / `DEV_TASK_MEM` | task sizing | `4096` / `16384` |
| `DEV_EIP_ALLOCS` | EIP allocation ids for the NLB (AWS) | — |
| `DEV_AWS_ACCOUNT` / `DEV_AWS_REGION` | stack env (falls back to `CDK_DEFAULT_*`, then `000000000000` / `us-east-1`) | MiniStack defaults |
| `DEV_ASSIGN_PUBLIC_IP` | AWS mode: `true` gives the task a public IP (no NAT needed; pair with public subnets in `DEV_SUBNET_IDS`) | `false` |

## Real AWS deployment

`deploy-aws.sh` wraps the full flow: ECR push → AWS-mode synth →
`cloudformation deploy`. Prereqs:

- AWS credentials in the environment (or a profile via `AWS_PROFILE`).
- An existing VPC: private subnet ids for the task (`DEV_SUBNET_IDS`),
  public subnet ids for the NLB (`DEV_PUBLIC_SUBNET_IDS`), and enough
  networking for the task to pull images and reach SSM/EFS (NAT gateway or
  `DEV_ASSIGN_PUBLIC_IP=true`, which puts the task on public subnets with an
  auto-assigned public IP).
- The pinned tool layers are x86_64-only; deploy into `amd64` Fargate.

```bash
export DEV_AWS_REGION=us-east-1
export DEV_VPC_ID=vpc-xxxx
export DEV_SUBNET_IDS=subnet-aaaa,subnet-bbbb        # private, task
export DEV_PUBLIC_SUBNET_IDS=subnet-cccc,subnet-dddd # public, NLB
export DEV_USER=<user>

bash ecs/deploy-aws.sh
```

What the script does:

1. `aws sts get-caller-identity` sanity print; fails fast on unset vars.
2. Creates the ECR repository idempotently (`devvm` by default), logs docker
   in, tags `ecs-dev-vm:<user>` as `<account>.dkr.ecr.<region>.amazonaws.com/devvm:<user>`
   and pushes (builds the image first if the local tag is missing).
3. Synthesizes without `MINISTACK` (`DEV_IMAGE=<ecr-uri>:<user>`) and asserts
   the template carries EFS, NLB and the ECR pull grant.
4. `aws cloudformation deploy --stack-name ecs-dev-<user> --capabilities CAPABILITY_IAM`.
5. On redeploys, forces a new ECS deployment so tasks re-pull the image by
   tag, then prints the stack outputs (NLB DNS name among them).

After it finishes: `bash ecs/test_ssh.sh <nlb-dns> ~/.ssh/id_rsa <user>`.
SSH works via the NLB DNS name (stable) or the task private IP (rotates on
task recycle). `ecs_vm.py` also works against real AWS: unset
`AWS_ENDPOINT_URL` and use real credentials (`status`, `restart`, `logs`).

Teardown: `aws cloudformation delete-stack --stack-name ecs-dev-<user>`.
The EFS filesystem is `RETAIN` — delete it manually afterwards. Cost note:
while deployed you pay for the Fargate task, the NLB (+ LCU), EFS storage
and (without `DEV_ASSIGN_PUBLIC_IP`) NAT gateway hours. The security group
opens TCP 22 to `0.0.0.0/0` — tighten it for real use.

## Design notes (why it looks like this)

- **Template pre-flight**: MiniStack's CFN engine rejects unknown types. The
  MiniStack template therefore contains only `AWS::SSM::Parameter`,
  `AWS::EC2::SecurityGroup` (inline `CidrIp` ingress — SG-to-SG rules are not
  parsed), `AWS::ECS::{Cluster,TaskDefinition,Service}` and
  `AWS::IAM::{Role,Policy}`. VPC/subnets are imported by id; no EIP/NAT/EFS/NLB.
- **SSH key**: a public key is stored as an SSM `String` parameter
  (`/devboxes/<user>/ssh-public-key`) for parity, but MiniStack's CFN-created
  task-def `secrets` are not resolved at start (key casing is not normalized
  by the CFN provisioner and SecureString is unsupported), so on MiniStack the
  key also flows as a plain env var; on AWS it is additionally resolved via
  `secrets` from SSM.
- **Nested docker** (`DEV_DOCKER_MODE`):
  - `root` (MiniStack): plain dockerd inside the task container, `vfs`
    storage, `bridge=none` (no iptables needed). The task container runs with
    `privileged: true` because the host security module only grants
    `MS_BIND`/`MS_SETFLAGS` (mounting layer dirs, remounts) to privileged
    containers — capability lists alone are not enough. Your shell talks to it
    via `DOCKER_HOST=unix:///var/run/docker-dind.sock`.
  - `rootless` (AWS/Fargate default): `dockerd-rootless.sh` as the dev user,
    `vfs` driver, TUN device auto-created by the entrypoint. Fargate forbids
    `privileged`, and rootlesskit works there because Fargate's task mount
    namespace is not owned by the initial user namespace.
  - Rootless was proven impossible on MiniStack: rootlesskit requires a mount
    namespace not owned by the initial user namespace, which a plain outer
    Docker daemon cannot provide (fails with
    `failed to share mount point: /: permission denied` even with SYS_ADMIN).
- **Persistence**: MiniStack mode bind-mounts `DEV_DATA_DIR` to
  `/home/<user>`; task stop/start (or MiniStack restart, thanks to
  `PERSIST_STATE=1` + service reconciliation) preserves everything. AWS mode
  uses EFS with an access point pinned to uid/gid 1000 instead.
- **SSH host keys** are generated at image build time and restored by the
  entrypoint, so the host key survives task recycling (rebuilds change it —
  then `ssh-keygen -R <ip>`).
- **awsvpc**: the task IP (`attachments[].details[privateIPv4Address]`) is
  directly reachable from the host, so SSH needs no port publishing;
  `containerPort == hostPort == 22`.
- **ECR pull grant**: the execution role is created explicitly, so CDK's
  automatic ECR grants never apply; when `DEV_IMAGE` contains `.dkr.ecr.`
  the stack does `Repository.fromRepositoryName(...).grantPull(executionRole)`.
- **Pinned tools**: Node.js, Kilo Code, Termide and Herdr are installed from
  fixed versions with checksum verification in the Dockerfile (`*_SHA256`
  ARGs); bumping a version requires the matching new digest.

## Troubleshooting

- `task STOPPED / Essential container exited` → `ecs_vm.py logs`, and
  `docker logs ministack-ecs-<id8>-devvm` for the container itself.
- Permission denied on SSH right after a rebuild → host key changed:
  `ssh-keygen -R <ip>`.
- Docker "permission denied ... /var/run/docker-dind.sock" → you are not in
  the `docker` group in this image build (rebuild with current Dockerfile).
- Disk pressure during image builds → `docker builder prune -f` (the image is
  ~4.5 GB; VSCode + Docker CE dominate).