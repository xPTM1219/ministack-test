# Profile snippet for the devbox: interactive shells talk to the *rootless*
# docker daemon running inside the task, not the host daemon.
export DOCKER_HOST=unix:///run/user/1000/docker.sock
export XDG_RUNTIME_DIR=/run/user/1000