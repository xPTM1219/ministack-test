FROM public.ecr.aws/amazonlinux/amazonlinux:2023

RUN dnf update -y \
    && dnf install aws-cli -y

# Mount here


ENTRYPOINT ['/usr/bin/bash']
