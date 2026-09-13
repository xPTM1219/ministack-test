# ECS Fargate Dev VM ("devvm")

A per-user development VM that runs as an **ECS Fargate task**: Rocky 9 + sshd
(pubkey auth) + XFCE + VS Code + a nested Docker daemon, with the home
directory persisted across task restarts. The same CDK stack deploys to
MiniStack (local) and real AWS.

```
ecs/
├── Dockerfile            Rocky 9 image: sshd, XFCE, VSCode, Docker CE
├── image/
│   ├── supervisord.conf  PID-1 config (sshd + dockerd; rendered per-mode)
│   ├── entrypoint.sh     installs SSH key, prepares runtime, renders config
│   └── 99-devbox.sh      profile: DOCKER_HOST for the nested daemon
├── build-image.sh        builds ecs-dev-vm:<user> with the host docker
├── cdk/                  Java CDK app (pom.xml, EcsDevVmApp, EcsDevVmStack)
│   └── cdk.out/          synthesized templates (MiniStack + AWS modes)
├── ecs_vm.py             ops CLI: status/ssh/start/stop/restart/logs/exec
└── test_ssh.sh           automated SSH acceptance test
```

## Quick start (MiniStack)

```bash
# 0. one-time: CDK bootstrap shim + a bind dir for /home/<user>
aws ssm put-parameter --endpoint-url http://localhost:4566 \
    --name /cdk-bootstrap/hnb659fds/version --type String --value 5
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
| `DEV_IMAGE` | local image ref | `ecs-dev-vm:<user>` |
| `DEV_SSH_PUBLIC_KEY` | pubkey contents (required) | — |
| `DEV_VPC_ID` / `DEV_SUBNET_IDS` | imported VPC + private subnets | — |
| `DEV_PUBLIC_SUBNET_IDS` | subnets for the NLB (AWS mode) | — |
| `DEV_SG_IDS` | extra security groups | — |
| `DEV_DATA_DIR` | absolute host path for `/home/<user>` bind | required in MiniStack |
| `DEV_TASK_CPU` / `DEV_TASK_MEM` | task sizing | `4096` / `16384` |
| `DEV_EIP_ALLOCS` | EIP allocation ids for the NLB (AWS) | — |

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

## Troubleshooting

- `task STOPPED / Essential container exited` → `ecs_vm.py logs`, and
  `docker logs ministack-ecs-<id8>-devvm` for the container itself.
- Permission denied on SSH right after a rebuild → host key changed:
  `ssh-keygen -R <ip>`.
- Docker "permission denied ... /var/run/docker-dind.sock" → you are not in
  the `docker` group in this image build (rebuild with current Dockerfile).
- Disk pressure during image builds → `docker builder prune -f` (the image is
  ~4.5 GB; VSCode + Docker CE dominate).