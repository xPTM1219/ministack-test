# ECS Fargate dev VM — Rocky 9 with sshd + XFCE + VSCode + rootless Docker.
# PID 1 is supervisord: sshd (foreground) and a rootless dockerd running as
# user `dev` (uid/gid 1000, matching the EFS access point posixUser on AWS).
FROM public.ecr.aws/docker/library/rockylinux:9

ARG DEV_USER=dev
ARG DEV_UID=1000
ARG DEV_GID=1000

# --- Pinned dev tools (x86_64 only; Fargate default arch) --------------------
# Each install layer verifies the artifact checksum and fails the build on a
# mismatch. Bumping a version requires the matching new digest.
ARG NODE_VERSION=24.21.0
ARG KILO_VERSION=7.8.8
ARG KILO_SHA256=1072997aa8d54f001cacf76527465353b7a7abd76b7e08828309bd3576c6429b
ARG TERMIDE_VERSION=0.40.0
ARG TERMIDE_SHA256=16789c2251f98623105413dbd3afd7a1c426821a01730f91dc061edda234547e
ARG HERDR_VERSION=0.9.3
ARG HERDR_SHA256=18a8dc65f1c2fa485884344356dea1cfd911c6f06cf46fa78e193f4087f4dba7

# --- Base packages ---------------------------------------------------------
# Rocky 9 notes: XFCE is split across EPEL9 packages (no `xfce4` meta), and
# newuidmap/newgidmap ship inside shadow-utils (no separate `uidmap` pkg).
RUN dnf -y install dnf-plugins-core epel-release \
    && dnf config-manager --set-enabled crb \
    && dnf -y group install "Development Tools" \
    && dnf -y install \
        openssh-server openssh-clients xorg-x11-xauth \
        xfce4-session xfce4-panel xfwm4 xfdesktop xfce4-settings \
        xfce4-terminal xfce4-whiskermenu-plugin dbus-x11 \
        git jq rsync htop vim tmux awscli2 \
        fuse-overlayfs slirp4netns shadow-utils iproute \
        sudo passwd which findutils procps-ng \
        python3 python3-pip \
    && dnf clean all

# --- Microsoft repo (VSCode) -----------------------------------------------
RUN rpm --import https://packages.microsoft.com/keys/microsoft.asc \
    && printf '[code]\nname=Visual Studio Code\nbaseurl=https://packages.microsoft.com/yumrepos/vscode\nenabled=1\ngpgcheck=1\ngpgkey=https://packages.microsoft.com/keys/microsoft.asc\n' > /etc/yum.repos.d/vscode.repo \
    && dnf -y install code \
    && dnf clean all

# --- Docker CE (incl. rootless extras) --------------------------------------
RUN dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo \
    && dnf -y install docker-ce docker-ce-cli containerd.io \
        docker-compose-plugin docker-buildx-plugin docker-ce-rootless-extras \
    && dnf clean all

# --- dev user ---------------------------------------------------------------
# /etc/subuid may already carry a rootless-extras entry for the user; dedupe so
# newuidmap never sees an overlapping map (write to uid_map fails otherwise).
# Membership in the `docker` group (created by docker-ce) lets the dev user
# talk to the DinD daemon socket (root mode) without sudo.
RUN groupadd -g ${DEV_GID} ${DEV_USER} \
    && useradd -m -u ${DEV_UID} -g ${DEV_GID} -s /bin/bash -G docker ${DEV_USER} \
    && echo "${DEV_USER} ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/${DEV_USER} \
    && grep -q "^${DEV_USER}:" /etc/subuid 2>/dev/null \
        || echo "${DEV_USER}:100000:65536" >> /etc/subuid \
    && grep -q "^${DEV_USER}:" /etc/subgid 2>/dev/null \
        || echo "${DEV_USER}:100000:65536" >> /etc/subgid \
    && printf '%s\n' "$(sort -u /etc/subuid)" > /etc/subuid \
    && printf '%s\n' "$(sort -u /etc/subgid)" > /etc/subgid

# --- sshd: passwordless pubkey auth, X11 forwarding -------------------------
RUN mkdir -p /run/sshd /home/${DEV_USER}/.ssh \
    && ssh-keygen -A \
    && printf 'PermitRootLogin no\nPubkeyAuthentication yes\nPasswordAuthentication no\nChallengeResponseAuthentication no\nX11Forwarding yes\nAllowTcpForwarding yes\nStrictModes no\n' > /etc/ssh/sshd_config.d/50-devbox.conf \
    && chown -R ${DEV_UID}:${DEV_GID} /home/${DEV_USER}/.ssh \
    && chmod 700 /home/${DEV_USER}/.ssh

# Stable SSH host keys are generated at build time so they survive redeploys
# as long as the image is not rebuilt.
RUN mkdir -p /etc/ssh/hostkeys \
    && cp /etc/ssh/ssh_host_*_key* /etc/ssh/hostkeys/

# --- Image assets -----------------------------------------------------------
# supervisord.conf references ${DEV_USER}; the entrypoint renders it before
# starting (supervisord has no runtime user substitution).
COPY image/supervisord.conf /etc/supervisord.conf.tpl
COPY image/entrypoint.sh /usr/local/bin/entrypoint.sh
COPY image/99-devbox.sh /etc/profile.d/99-devbox.sh
RUN chmod +x /usr/local/bin/entrypoint.sh /etc/profile.d/99-devbox.sh \
    && chown ${DEV_UID}:${DEV_GID} /etc/profile.d/99-devbox.sh

# supervisord needs a python runtime on EL9 (it is not packaged in rocky base)
RUN dnf -y install python3-setuptools \
    && dnf clean all \
    && curl -fsSL https://raw.githubusercontent.com/Supervisor/supervisor/main/setup.py -o /dev/null \
    || true

# Install supervisord via pip (no distro package on Rocky 9)
RUN python3 -m pip install --no-cache-dir supervisor

# --- Node.js (runtime for npm-based tooling) ---------------------------------
# Checksum comes from nodejs.org's own SHASUMS256.txt (same RUN). Installs to
# /usr/local/node; the npm/npx shims resolve sibling paths via symlinks.
RUN curl -fsSL https://nodejs.org/dist/v${NODE_VERSION}/SHASUMS256.txt -o /tmp/SHASUMS256.txt \
    && curl -fsSL https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz -o /tmp/node-v${NODE_VERSION}-linux-x64.tar.xz \
    && cd /tmp \
    && grep " node-v${NODE_VERSION}-linux-x64.tar.xz\$" SHASUMS256.txt | sha256sum -c - \
    && mkdir -p /usr/local/node \
    && tar -xJf node-v${NODE_VERSION}-linux-x64.tar.xz -C /usr/local/node --strip-components=1 \
    && ln -sfn /usr/local/node/bin/node /usr/local/bin/node \
    && ln -sfn /usr/local/node/bin/npm /usr/local/bin/npm \
    && ln -sfn /usr/local/node/bin/npx /usr/local/bin/npx \
    && node -v \
    && rm -f SHASUMS256.txt node-v${NODE_VERSION}-linux-x64.tar.xz

# --- Kilo Code CLI -----------------------------------------------------------
# The tarball carries the kilo binary plus sandbox helpers (bwrap, seccomp,
# relay js) and wasm/console assets that the binary reads relative to its own
# path, so the whole tree goes to /usr/local/lib/kilo; only the symlink lands
# in /usr/local/bin.
RUN curl -fsSL https://github.com/Kilo-Org/kilocode/releases/download/v${KILO_VERSION}/kilo-linux-x64.tar.gz -o /tmp/kilo.tar.gz \
    && echo "${KILO_SHA256}  /tmp/kilo.tar.gz" | sha256sum -c - \
    && mkdir -p /usr/local/lib/kilo \
    && tar -xzf /tmp/kilo.tar.gz -C /usr/local/lib/kilo \
    && ln -sfn /usr/local/lib/kilo/kilo /usr/local/bin/kilo \
    && timeout 60 /usr/local/bin/kilo --version \
    && rm -f /tmp/kilo.tar.gz

# --- Termide (TUI IDE, glibc build) ------------------------------------------
RUN curl -fsSL https://github.com/termide/termide/releases/download/${TERMIDE_VERSION}/termide-${TERMIDE_VERSION}-x86_64-unknown-linux-gnu.tar.gz -o /tmp/termide.tar.gz \
    && echo "${TERMIDE_SHA256}  /tmp/termide.tar.gz" | sha256sum -c - \
    && tar -xzf /tmp/termide.tar.gz -C /tmp \
    && install -m 755 /tmp/termide /usr/local/bin/termide \
    && timeout 60 /usr/local/bin/termide --version \
    && rm -rf /tmp/termide /tmp/termide.tar.gz

# --- Herdr (terminal pane manager, static binary) ----------------------------
RUN curl -fsSL https://github.com/herdrdev/herdr/releases/download/v${HERDR_VERSION}/herdr-linux-x86_64 -o /tmp/herdr \
    && echo "${HERDR_SHA256}  /tmp/herdr" | sha256sum -c - \
    && install -m 755 /tmp/herdr /usr/local/bin/herdr \
    && timeout 60 /usr/local/bin/herdr --version \
    && rm -f /tmp/herdr

EXPOSE 22

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["/usr/local/bin/supervisord", "-c", "/etc/supervisord.conf", "--nodaemon"]