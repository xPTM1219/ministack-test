# Java Lambda

This Lambda is made to prove and learn how to deploy and invoke
Lambdas in Ministack.

## Report

The Java lambda now invokes successfully. The invoke command itself was never the problem — the standard command works:

```bash
aws --endpoint-url=${AWS_ENDPOINT_URL} lambda invoke --function-name JavaGetQueue --region ${AWS_REGION} --log-type Tail response.json | jq -r '.LogResult' | base64 -d
```

### Root causes (3 stacked issues)

1. **Docker network isolation (the `handler=` error).** Ministack runs in DinD mode (docker.sock mounted) and spawns Lambda RIE containers on the default `bridge` network, while ministack itself is on `ministack_default`. Docker blocks cross-network traffic between bridge and user-defined networks, so ministack's HTTP POST to the RIE endpoint (`:8080`) timed out and fell into the "never became reachable" error path, dumping the container log (`exec '/var/runtime/bootstrap' ... handler=`) as the error. The handler setup was actually fine — verified by manually POSTing to a RIE container with the same jar: it returned the handler's response.

2. **Timeout 3s / memory 128MB** — too small for a JVM cold start; the RIE connect deadline is only `timeout + 2s`.

### Fixes applied

- `docker-compose.yml`: added `LAMBDA_DOCKER_NETWORK=ministack_default` so lambda containers join ministack's network (recreated with `MINISTACK_VERSION=1.5.13-full docker compose up -d`).
- `prepare_ministack`: added a Java lambda provisioning block — exploded shaded-jar zip, handler `org.xptm.GetQueueHandler::handleRequest`, `--timeout 60 --memory-size 1024`, and `SQS_ENDPOINT_URL=http://host.docker.internal:4566`.
- Live function config updated via `update-function-configuration` (same env fix + 60s/1024MB).

### Verified results

- Cold start: full invocation in ~2.4s, `statusCode: 200` with all `test-queue` messages returned (bodies parsed as JSON)
- Warm invocation: 8.9ms
- Ministack logs show `INVOKE RTDONE(status: success)` from rapid

