#!/bin/bash
# Task entrypoint: install the SSM-injected SSH public key, prepare the
# docker runtime, then exec supervisord (PID 1).
set -euo pipefail

DEV_USER="${DEV_USER:-xptm}"
DEV_HOME="/home/${DEV_USER}"
DEV_UID="$(id -u ${DEV_USER})"
DEV_GID="$(id -g ${DEV_USER})"
# rootless = dockerd-rootless.sh as the dev user (AWS/Fargate, where it works)
# root    = plain dockerd inside the container (MiniStack DinD fallback:
#           rootlesskit needs a mount ns owned by a non-initial user ns,
#           which a plain outer Docker daemon cannot provide)
DEV_DOCKER_MODE="${DEV_DOCKER_MODE:-rootless}"

# --- authorized_keys --------------------------------------------------------
# On MiniStack the key arrives as a plain env var (DEV_SSH_PUBLIC_KEY);
# on AWS the same variable is resolved from SSM Parameter Store via the
# container's `secrets` block.
if [ -n "${DEV_SSH_PUBLIC_KEY:-}" ]; then
    mkdir -p "${DEV_HOME}/.ssh"
    printf '%s\n' "${DEV_SSH_PUBLIC_KEY}" > "${DEV_HOME}/.ssh/authorized_keys"
    chmod 700 "${DEV_HOME}/.ssh"
    chmod 600 "${DEV_HOME}/.ssh/authorized_keys"
    chown -R "${DEV_UID}:${DEV_GID}" "${DEV_HOME}/.ssh"
fi

# Restore stable SSH host keys generated at build time (a fresh container
# would otherwise regenerate them and change the host key fingerprint).
if [ -d /etc/ssh/hostkeys ]; then
    cp /etc/ssh/hostkeys/* /etc/ssh/ 2>/dev/null || true
fi

# --- docker runtime ----------------------------------------------------------
XDG_DIR="/run/user/${DEV_UID}"
mkdir -p "${XDG_DIR}"
chown "${DEV_UID}:${DEV_GID}" "${XDG_DIR}"
chmod 700 "${XDG_DIR}"

# slirp4netns/rootlesskit need the TUN device node; task containers may not
# get one pre-created (Fargate provides it).
if [ ! -e /dev/net/tun ]; then
    mkdir -p /dev/net 2>/dev/null || true
    mknod /dev/net/tun c 10 200 2>/dev/null && chmod 666 /dev/net/tun \
        && echo "entrypoint: created /dev/net/tun" || true
fi

# The dev-home volume may be mounted at the home dir; make sure it is owned
# by the dev user (bind mounts are created root-owned by docker).
chown "${DEV_UID}:${DEV_GID}" "${DEV_HOME}" 2>/dev/null || true

# Welcome file so first logins show something recognizable
if [ ! -f "${DEV_HOME}/.bashrc" ]; then
    cp /etc/skel/.bashrc "${DEV_HOME}/.bashrc" 2>/dev/null || true
    cp /etc/skel/.bash_profile "${DEV_HOME}/.bash_profile" 2>/dev/null || true
    chown "${DEV_UID}:${DEV_GID}" "${DEV_HOME}/.bashrc" "${DEV_HOME}/.bash_profile" 2>/dev/null || true
fi

# Render the supervisord config and keep only the dockerd program matching
# DEV_DOCKER_MODE (supervisord has no runtime user substitution).
export DEV_UID DEV_GID DEV_USER DEV_HOME DEV_DOCKER_MODE
if [ -f /etc/supervisord.conf.tpl ]; then
    envsubst '${DEV_USER} ${DEV_HOME} ${DEV_UID}' \
        < /etc/supervisord.conf.tpl > /etc/supervisord.conf
    case "${DEV_DOCKER_MODE}" in
        rootless)  drop="program:dockerd-root" ;;
        root)      drop="program:dockerd-rootless" ;;
        *)
            echo "entrypoint: unknown DEV_DOCKER_MODE '${DEV_DOCKER_MODE}' (rootless|root)" >&2
            exit 1
            ;;
    esac
    awk -v drop="[${drop}]" '
        /^\[program:/ { skip = ($0 == drop) }
        !skip { print }
    ' /etc/supervisord.conf > /etc/supervisord.conf.new \
        && mv /etc/supervisord.conf.new /etc/supervisord.conf
fi

# Interactive shells use the daemon selected above.
if [ "${DEV_DOCKER_MODE}" = "root" ]; then
    printf 'export DOCKER_HOST=unix:///var/run/docker-dind.sock\nexport XDG_RUNTIME_DIR=/run/user/%s\n' "${DEV_UID}" \
        > /etc/profile.d/99-devbox.sh
else
    printf 'export DOCKER_HOST=unix:///run/user/%s/docker.sock\nexport XDG_RUNTIME_DIR=/run/user/%s\n' \
        "${DEV_UID}" "${DEV_UID}" > /etc/profile.d/99-devbox.sh
fi
chown "${DEV_UID}:${DEV_GID}" /etc/profile.d/99-devbox.sh

echo "entrypoint: starting supervisord (sshd + ${DEV_DOCKER_MODE} dockerd as ${DEV_USER})"
exec "$@"