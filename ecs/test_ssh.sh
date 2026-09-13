#!/bin/bash
# Automated SSH acceptance test for the ECS dev VM.
# Usage: bash test_ssh.sh <task-ip> [private-key] [ssh-user]
# The public key matching [private-key] (default ~/.ssh/id_rsa.pub) must have
# been passed as DEV_SSH_PUBLIC_KEY when the stack was deployed, and
# [ssh-user] (3rd arg or DEV_USER, default xptm) must match DEV_USER there.
set -uo pipefail

IP="${1:?usage: test_ssh.sh <task-ip> [private-key] [ssh-user]}"
KEY="${2:-$HOME/.ssh/id_rsa}"
SSH_USER="${3:-${DEV_USER:-xptm}}"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -o BatchMode=yes"
SSH="ssh $SSH_OPTS -i $KEY $SSH_USER@$IP"

pass=0
fail=0

run() {
    local label="$1"; shift
    echo "--- $label"
    if "$@"; then
        pass=$((pass+1))
    else
        fail=$((fail+1))
        echo "FAILED: $label"
    fi
}

# 1. connectivity + basic login
run "ssh login (whoami)" $SSH whoami

# 2. XFCE + tooling present
run "code --version" $SSH code --version
run "xfce4 present" $SSH test -x /usr/bin/xfce4-session
run "dev tools present" $SSH "git --version; jq --version; tmux -V"

# 3. nested docker daemon (rootless on AWS; DinD fallback on MiniStack)
run "nested docker daemon up" $SSH "docker info --format '{{.ServerVersion}}'"
run "hello-world in nested docker" $SSH "docker run --rm hello-world"

# 4. home volume write/read (persistence is proven separately by ecs_vm.py)
run "home write/read" $SSH "echo test-acceptance > ~/acceptance.txt; grep -q test-acceptance ~/acceptance.txt"

echo
echo "acceptance: $pass passed, $fail failed"
exit $((fail > 0))