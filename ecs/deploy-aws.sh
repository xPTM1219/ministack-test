#!/bin/bash
# Deploy the ECS dev VM stack to real AWS: push the dev-vm image to ECR,
# synthesize the CDK template in AWS mode, then run a cloudformation deploy.
#
# Env-driven, no required positional args; aborts when a required input is
# missing. Re-running the same command re-deploys (a same-tag image push is
# picked up via --force-new-deployment on the ECS service).
#
# Required env:
#   DEV_VPC_ID              imported VPC id
#   DEV_SUBNET_IDS          private subnet ids for the Fargate task
#   DEV_PUBLIC_SUBNET_IDS   public subnet ids for the internet-facing NLB
#   DEV_SSH_PUBLIC_KEY      pubkey contents (falls back to ~/.ssh/id_rsa.pub)
#   DEV_AWS_REGION          region (falls back to AWS_REGION)
# Optional env:
#   DEV_USER                username (default xptm)
#   DEV_AWS_ACCOUNT         account id (default: resolved via sts)
#   DEV_ASSIGN_PUBLIC_IP    true = tasks get public IPs (no NAT needed)
#   ECR_REPO                ECR repository name (default devvm)
#   DEV_SG_IDS, DEV_TASK_CPU, DEV_TASK_MEM, DEV_EIP_ALLOCS  pass-through
#   JAVA_HOME               default /usr/lib/jvm/java-21-openjdk-amd64
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_USER="${DEV_USER:-xptm}"
ECR_REPO="${ECR_REPO:-devvm}"
STACK="ecs-dev-${DEV_USER}"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"

fail() { echo "ERROR: $1" >&2; exit 1; }

REGION="${DEV_AWS_REGION:-${AWS_REGION:-}}"
[ -n "$REGION" ] || fail "set DEV_AWS_REGION (or AWS_REGION)"
[ -n "${DEV_VPC_ID:-}" ] || fail "set DEV_VPC_ID"
[ -n "${DEV_SUBNET_IDS:-}" ] || fail "set DEV_SUBNET_IDS (private subnets for the task)"
[ -n "${DEV_PUBLIC_SUBNET_IDS:-}" ] || fail "set DEV_PUBLIC_SUBNET_IDS (public subnets for the NLB)"

if [ -z "${DEV_SSH_PUBLIC_KEY:-}" ]; then
    [ -f "$HOME/.ssh/id_rsa.pub" ] \
        || fail "set DEV_SSH_PUBLIC_KEY (or provide ~/.ssh/id_rsa.pub)"
    export DEV_SSH_PUBLIC_KEY="$(cat "$HOME/.ssh/id_rsa.pub")"
fi

echo "== caller identity"
aws --region "$REGION" sts get-caller-identity --query "[Account,Arn]" --output text

ACCOUNT="$(aws --region "$REGION" sts get-caller-identity --query Account --output text)"
export DEV_AWS_ACCOUNT="${DEV_AWS_ACCOUNT:-$ACCOUNT}"
export DEV_AWS_REGION="$REGION"
ECR_URI="${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com/${ECR_REPO}"
IMAGE="${ECR_URI}:${DEV_USER}"

echo "== ECR: repository + image push (${IMAGE})"
aws --region "$REGION" ecr describe-repositories --repository-names "$ECR_REPO" >/dev/null 2>&1 \
    || aws --region "$REGION" ecr create-repository --repository-name "$ECR_REPO" >/dev/null
aws --region "$REGION" ecr get-login-password \
    | docker login --username AWS --password-stdin "$ECR_URI" >/dev/null

LOCAL_IMAGE="ecs-dev-vm:${DEV_USER}"
if [ -z "$(docker image ls -q "$LOCAL_IMAGE")" ]; then
    bash "${DIR}/build-image.sh" "$DEV_USER"
fi
docker tag "$LOCAL_IMAGE" "$IMAGE"
docker push "$IMAGE"

echo "== CDK synth (AWS mode)"
unset MINISTACK
export JAVA_HOME DEV_IMAGE="$IMAGE"
( cd "${DIR}/cdk" && mvn -q compile exec:java )

TPL="${DIR}/cdk/cdk.out/EcsDevVmStack.template.json"
grep -q '"AWS::EFS' "$TPL" || fail "template has no EFS resources"
grep -q '"AWS::ElasticLoadBalancingV2' "$TPL" || fail "template has no NLB resources"
grep -q '"ecr:GetAuthorizationToken"' "$TPL" \
    || fail "template has no ECR pull grant on the execution role"

echo "== cloudformation deploy (${STACK})"
STACK_EXISTS=0
aws --region "$REGION" cloudformation describe-stacks --stack-name "$STACK" >/dev/null 2>&1 \
    && STACK_EXISTS=1

aws --region "$REGION" cloudformation deploy \
    --stack-name "$STACK" \
    --template-file "$TPL" \
    --capabilities CAPABILITY_IAM

# The image tag is the user name, so a rebuilt image shares the tag with the
# old one; force a new deployment so tasks re-pull by tag after a redeploy.
if [ "$STACK_EXISTS" = "1" ]; then
    echo "== force new deployment (re-pull ${IMAGE})"
    aws --region "$REGION" ecs update-service \
        --cluster "dev-${DEV_USER}" --service "devvm-${DEV_USER}" \
        --force-new-deployment >/dev/null
fi

echo "== stack outputs"
aws --region "$REGION" cloudformation describe-stacks --stack-name "$STACK" \
    --query "Stacks[0].Outputs[].{Key:OutputKey,Value:OutputValue}" --output table

cat <<EOF

Next steps:
  bash ${DIR}/test_ssh.sh <nlb-dns-or-task-ip> ~/.ssh/id_rsa ${DEV_USER}
Notes:
  - EFS is RETAIN-on-delete: delete the filesystem manually after stack delete
  - the nested docker daemon cannot publish public ports; use SSH port forwards
  - the NLB idle timeout is 350s; enable SSH keepalives for long sessions
EOF