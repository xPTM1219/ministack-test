# ECS Fargate dev VM — Rocky 9 with sshd + XFCE + VSCode + rootless Docker.
# PID 1 is supervisord: sshd (foreground) and a rootless dockerd running as
# user `dev` (uid/gid 1000, matching the EFS access point posixUser on AWS).
FROM public.ecr.aws/docker/library/rockylinux:9

ARG DEV_USER=dev
ARG DEV_UID=1000
ARG DEV_GID=1000

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
        git jq rsync htop vim tmux aws-cli \
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

EXPOSE 22

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["/usr/local/bin/supervisord", "-c", "/etc/supervisord.conf", "--nodaemon"]