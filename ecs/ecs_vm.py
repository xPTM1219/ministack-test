#!/usr/bin/env python3
"""Ops CLI for the ECS Fargate dev VM (devvm).

Works against MiniStack (default) or real AWS (export AWS_ENDPOINT_URL
unset and standard AWS creds). Subcommands:

    status              cluster/service/task summary incl. task private IP
    ssh                 print (or run with --) the SSH command to the task IP
    start               set desired-count 1 and wait for a running task
    stop                set desired-count 0 (home volume data is preserved)
    logs                container logs of the devvm task
    restart             stop + start in one go
    exec -- <cmd>       break-glass: run a command via docker exec (MiniStack)

Env: AWS_ENDPOINT_URL (default http://localhost:4566), AWS_ACCESS_KEY_ID /
AWS_SECRET_ACCESS_KEY (default test/test), AWS_REGION (default us-east-1),
DEV_CLUSTER (default dev-<user>), DEV_SERVICE (default devvm-<user>),
DEV_USER (default xptm).
"""

import os
import subprocess
import sys
import time

import boto3
from botocore.config import Config

DEV_USER = os.environ.get("DEV_USER", "xptm")
ENDPOINT = os.environ.get("AWS_ENDPOINT_URL", "http://localhost:4566")
REGION = os.environ.get("AWS_REGION", "us-east-1")
CLUSTER = os.environ.get("DEV_CLUSTER", f"dev-{DEV_USER}")
SERVICE = os.environ.get("DEV_SERVICE", f"devvm-{DEV_USER}")
FAMILY = os.environ.get("DEV_FAMILY", f"devvm-{DEV_USER}")
CONTAINER = os.environ.get("DEV_CONTAINER", "devvm")
SSH_USER = os.environ.get("DEV_SSH_USER", DEV_USER)

_config = Config(retries={"max_attempts": 10, "mode": "standard"}, read_timeout=30)


def _session():
    kwargs = dict(region_name=REGION)
    if ENDPOINT:
        kwargs["aws_access_key_id"] = os.environ.get("AWS_ACCESS_KEY_ID", "test")
        kwargs["aws_secret_access_key"] = os.environ.get("AWS_SECRET_ACCESS_KEY", "test")
    return boto3.Session(**kwargs)


def ecs():
    client_kwargs = dict(region_name=REGION, config=_config)
    if ENDPOINT:
        client_kwargs["endpoint_url"] = ENDPOINT
    return _session().client("ecs", **client_kwargs)


def task_ip(task):
    for att in task.get("attachments") or []:
        for d in att.get("details") or []:
            if d.get("name") == "privateIPv4Address":
                return d.get("value")
    return None


def find_tasks(status=None):
    c = ecs()
    arns = []
    paginator = c.get_paginator("list_tasks")
    for page in paginator.paginate(cluster=CLUSTER, family=FAMILY):
        arns.extend(page.get("taskArns") or [])
    if not arns:
        return []
    tasks = c.describe_tasks(cluster=CLUSTER, tasks=arns).get("tasks") or []
    if status:
        tasks = [t for t in tasks if t.get("lastStatus") == status]
    return tasks


def get_service():
    resp = ecs().describe_services(cluster=CLUSTER, services=[SERVICE])
    svcs = resp.get("services") or []
    return svcs[0] if svcs else None


def cmd_status(_args):
    svc = get_service()
    if not svc:
        print(f"service {CLUSTER}/{SERVICE}: NOT FOUND")
        return 1
    print(
        f"service {CLUSTER}/{SERVICE}: {svc['status']} "
        f"desired={svc.get('desiredCount')} running={svc.get('runningCount')}"
    )
    tasks = find_tasks()
    if not tasks:
        print("tasks: none")
        return 0
    for t in tasks:
        ip = task_ip(t)
        reason = t.get("stoppedReason") or ""
        line = (
            f"task {t['taskArn'].split('/')[-1]}: {t['lastStatus']} "
            f"(desired {t.get('desiredStatus')})"
        )
        if reason:
            line += f" stoppedReason={reason}"
        print(line)
        if ip:
            print(f"  ip: {ip}")
            print(f"  ssh: ssh {SSH_USER}@{ip}")
        for c in t.get("containers") or []:
            print(f"  container {c['name']}: {c.get('lastStatus')}")
    return 0


def cmd_ssh(args):
    tasks = find_tasks(status="RUNNING")
    if not tasks:
        print("no RUNNING task", file=sys.stderr)
        return 1
    ip = task_ip(tasks[0])
    if not ip:
        print("running task has no private IP yet", file=sys.stderr)
        return 1
    cmd = ["ssh", "-o", "StrictHostKeyChecking=accept-new", f"{SSH_USER}@{ip}"]
    if args:
        cmd += args
    print(" ".join(cmd), file=sys.stderr)
    os.execvp(cmd[0], cmd)


def set_desired(count, wait=False, timeout=300):
    c = ecs()
    c.update_service(cluster=CLUSTER, service=SERVICE, desiredCount=count)
    print(f"desiredCount -> {count}")
    if not wait:
        return 0
    deadline = time.time() + timeout
    while time.time() < deadline:
        svc = get_service()
        running = (svc or {}).get("runningCount") or 0
        if count == 0 and running == 0:
            print("no tasks running")
            return 0
        if count > 0:
            tasks = find_tasks(status="RUNNING")
            if tasks:
                ip = task_ip(tasks[0])
                print(f"task running, ip={ip}")
                return 0
        time.sleep(3)
    print("timed out waiting for service to converge", file=sys.stderr)
    return 1


def cmd_start(_args):
    return set_desired(1, wait="--no-wait" not in _args)


def cmd_stop(_args):
    return set_desired(0, wait="--no-wait" not in _args)


def cmd_restart(_args):
    rc = set_desired(0, wait=True)
    if rc != 0:
        return rc
    return set_desired(1, wait="--no-wait" not in _args)


def cmd_logs(_args):
    """Print docker logs of the devvm container (MiniStack: container name
    is ministack-ecs-<taskid8>-devvm; resolve via docker ps label filter)."""
    tasks = find_tasks()
    if not tasks:
        print("no tasks", file=sys.stderr)
        return 1
    task_id = tasks[0]["taskArn"].split("/")[-1]
    name = f"ministack-ecs-{task_id[:8]}-{CONTAINER}"
    return subprocess.call(["docker", "logs", "--tail", "200", name])


def cmd_exec(args):
    """docker exec into the devvm container on the host daemon (break-glass)."""
    if args and args[0] == "--":
        args = args[1:]
    tasks = find_tasks(status="RUNNING")
    if not tasks:
        print("no RUNNING task", file=sys.stderr)
        return 1
    task_id = tasks[0]["taskArn"].split("/")[-1]
    name = f"ministack-ecs-{task_id[:8]}-{CONTAINER}"
    tty = sys.stdin.isatty() and sys.stdout.isatty()
    cmd = ["docker", "exec"] + (["-it"] if tty else ["-i"]) + [name]
    cmd += args if args else ["bash"]
    print(" ".join(cmd), file=sys.stderr)
    return subprocess.call(cmd)


def main():
    commands = {
        "status": cmd_status,
        "ssh": cmd_ssh,
        "start": cmd_start,
        "stop": cmd_stop,
        "restart": cmd_restart,
        "logs": cmd_logs,
        "exec": cmd_exec,
    }
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        print(__doc__)
        return 0
    cmd, *rest = sys.argv[1:]
    if cmd not in commands:
        print(f"unknown command: {cmd}", file=sys.stderr)
        print(__doc__)
        return 2
    return commands[cmd](rest) or 0


if __name__ == "__main__":
    sys.exit(main())