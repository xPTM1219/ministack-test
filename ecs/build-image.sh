#!/bin/bash
# Build the ECS dev-VM image locally with the host docker daemon.
# MiniStack's ECS talks to the same daemon, so a locally built image is
# directly runnable by an ECS task (image reference = DEV_IMAGE).
#
# Usage: bash build-image.sh [DEV_USER]   (default: $DEV_USER or xptm)
set -euo pipefail

DEV_USER="${1:-${DEV_USER:-xptm}}"
TAG="ecs-dev-vm:${DEV_USER}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Building ${TAG} (this downloads Rocky 9 + VSCode + Docker CE; slow first time)"
docker build --build-arg DEV_USER="${DEV_USER}" --build-arg DEV_UID=1000 --build-arg DEV_GID=1000 \
    -t "${TAG}" "${DIR}"
echo "Built ${TAG}. Use DEV_IMAGE=${TAG} when deploying the CDK stack."