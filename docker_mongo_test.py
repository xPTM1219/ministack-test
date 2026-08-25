"""
Info https://pypi.org/project/docker/
"""

import docker


import os
# print(os.environ.get("DOCKER_HOST"))
# print(os.environ.get("DOCKER_CERT_PATH"))
# print(os.environ.get("DOCKER_CONTEXT"))


client = docker.from_env()

# client = docker.DockerClient(base_url='tcp://127.0.0.1')
image = "mongo:5.0.33"
container_name = "mongo"
container: str

# Listing running containers
running_containers = client.containers.list()
print(running_containers)

# Getting container daemon
try:
    container = client.containers.get('mongo')
except docker.errors.NotFound as error:
    # Creating container if it doesn't exists
    client.containers.run(image, name=container_name, detach=True)


# Getting the ID of the container
mongo_id = container.id

# Running commands inside the container
output = container.exec_run(cmd="mongosh --eval 'show dbs'")
print(output.output)
# print(container.exec_run(cmd="mongosh --eval 'show dbs'"))

