# WeatherApi — API Gateway via Java CDK (ministack)

Java CDK stack that creates the REST API (`WeatherApi`) with routes
`GET /weather` → `GetWeather` and `GET /queue` → `GetQueue`. The lambdas
themselves are **not** managed by CDK — they are created/updated by CLI
commands below (same pattern as `prepare_ministack`), because the functions
are containerized with dependency-heavy zips that the CLI handles well.

## Layout

```
apigw/
├── google-checkstyle.xml         Google Java Style config (user-provided)
├── checkstyle_supression.xml     empty suppressions file (name sic, matches pom propertyExpansion)
├── pom.xml                       Java 21, aws-cdk-lib 2.250.0, checkstyle at validate, exec:java
├── src/main/java/org/xptm/       ApigwApp.java, ApigwStack.java
├── src/test/java/org/xptm/       ApigwStackTest.java, ApigwAppTest.java (offline synth asserts)
└── cdk.out/                      generated: ApigwStack.template.json
```

## Prerequisites

```bash
# From the repo root. Ministack + bucket `weather-app` + queue `test-queue`
# are assumed already running (see ../prepare_ministack).
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

One-time CDK bootstrap shim (skip if already done for docdb):

```bash
aws --endpoint-url=$AWS_ENDPOINT_URL ssm put-parameter \
    --name /cdk-bootstrap/hnb659fds/version --type String --value 5
```

## Step 1 — Verify Mongo has weather data

`GetWeather` reads `xtemp.weather`. Check with a short pymongo one-liner:

```bash
python3 -c "
from pymongo import MongoClient
c = MongoClient('mongodb://<MONGO_HOST>:27017/')['xtemp']['weather']
docs = list(c.find({}, {'weather': 1}))
print(len(docs), 'docs;', sum(len(d.get('weather', {})) for d in docs), 'timestamps')"
```

If Mongo is not reachable, check the ministack README for how the weather
ingestor connects (the host IP may be `172.17.0.1` from a container instead
of `localhost`).

## Step 2 — Create / update the lambdas

Package and deploy `GetWeather` (hourly aggregation of `xtemp.weather`) and
`GetQueue` (SQS reader). Both use `host.docker.internal` because ministack
lambdas run containerized (Linux fallback: `172.17.0.1`).

```bash
export MONGO_HOST=host.docker.internal   # or 172.17.0.1

PKG_DIR=/tmp/lambda-pkg
mkdir -p "$PKG_DIR"

# GetWeather
rm -rf "$PKG_DIR"/*
cp lambda/get_weather.py "$PKG_DIR/"
pip install -q -r lambda/requirements.txt -t "$PKG_DIR" --upgrade
(cd "$PKG_DIR" && zip -qr /tmp/get_weather.zip .)
aws --endpoint-url=$AWS_ENDPOINT_URL lambda create-function \
    --function-name GetWeather \
    --runtime python3.12 \
    --role arn:aws:iam::000000000000:role/lambda-role \
    --handler get_weather.lambda_handler \
    --zip-file fileb:///tmp/get_weather.zip \
    --environment Variables="{MONGO_URI=mongodb://${MONGO_HOST}:27017/,DB_NAME=xtemp,COLLECTION_NAME=weather}" \
    || aws --endpoint-url=$AWS_ENDPOINT_URL lambda update-function-code \
    --function-name GetWeather --zip-file fileb:///tmp/get_weather.zip

aws --endpoint-url=$AWS_ENDPOINT_URL lambda update-function-configuration \
    --function-name GetWeather \
    --environment Variables="{MONGO_URI=mongodb://${MONGO_HOST}:27017/,DB_NAME=xtemp,COLLECTION_NAME=weather}"

# GetQueue
rm -rf "$PKG_DIR"/*
cp lambda/get_queue.py "$PKG_DIR/"
pip install -q -r lambda/requirements.txt -t "$PKG_DIR" --upgrade
(cd "$PKG_DIR" && zip -qr /tmp/get_queue.zip .)
aws --endpoint-url=$AWS_ENDPOINT_URL lambda create-function \
    --function-name GetQueue \
    --runtime python3.12 \
    --role arn:aws:iam::000000000000:role/lambda-role \
    --handler get_queue.lambda_handler \
    --zip-file fileb:///tmp/get_queue.zip \
    --environment Variables="{SQS_ENDPOINT_URL=http://${MONGO_HOST}:4566,QUEUE_URL=http://sqs.us-east-1.localhost.ministack.cloud:4566/000000000000/test-queue,MAX_MESSAGES=50}" \
    || aws --endpoint-url=$AWS_ENDPOINT_URL lambda update-function-code \
    --function-name GetQueue --zip-file fileb:///tmp/get_queue.zip

aws --endpoint-url=$AWS_ENDPOINT_URL lambda update-function-configuration \
    --function-name GetQueue \
    --environment Variables="{SQS_ENDPOINT_URL=http://${MONGO_HOST}:4566,QUEUE_URL=http://sqs.us-east-1.localhost.ministack.cloud:4566/000000000000/test-queue,MAX_MESSAGES=50}"

rm -rf "$PKG_DIR"
```

Smoke-test directly:

```bash
aws --endpoint-url=$AWS_ENDPOINT_URL lambda invoke \
    --function-name GetWeather /tmp/w.json && cat /tmp/w.json
aws --endpoint-url=$AWS_ENDPOINT_URL lambda invoke \
    --function-name GetQueue /tmp/q.json && cat /tmp/q.json
```

## Step 3 — Checkstyle + tests + synth

```bash
cd apigw
mvn test                     # checkstyle (Google style, validate phase) + JUnit synth asserts
mvn -q compile exec:java     # -> cdk.out/ApigwStack.template.json
```

Both commands are part of the normal Maven lifecycle — nothing extra to remember.

## Step 4 — Deploy the REST API

```bash
aws --endpoint-url=$AWS_ENDPOINT_URL cloudformation deploy \
    --stack-name WeatherApi \
    --template-file cdk.out/ApigwStack.template.json

aws --endpoint-url=$AWS_ENDPOINT_URL cloudformation describe-stacks \
    --stack-name WeatherApi --query 'Stacks[0].Outputs'

API_ID=$(aws --endpoint-url=$AWS_ENDPOINT_URL apigateway get-rest-apis \
    --query 'items[?name==`WeatherApi`].id' --output text)
echo "$API_ID"

curl -i "http://$API_ID.execute-api.localhost:4566/dev/weather"
curl -i "http://$API_ID.execute-api.localhost:4566/dev/queue"
# Alternative invoke path:
# curl "http://localhost:4566/_aws/execute-api/$API_ID/dev/weather"
```

### Fallback — direct CLI creation (if ministack CFN rejects AWS::ApiGateway::*)

```bash
API_ID=$(aws --endpoint-url=$AWS_ENDPOINT_URL apigateway create-rest-api \
    --name "WeatherApi" --query 'id' --output text)
ROOT_ID=$(aws --endpoint-url=$AWS_ENDPOINT_URL apigateway get-resources \
    --rest-api-id $API_ID --query 'items[?path==`/`].id' --output text)

for ROUTE in weather queue; do
  FN=$(echo $ROUTE | sed 's/^\(w\)/GetW/;s/^\(q\)/GetQ/' | sed 's/^\(.*\)$/\1/')
  case $ROUTE in
    weather) FN=GetWeather ;;
    queue)   FN=GetQueue ;;
  esac
  RES_ID=$(aws --endpoint-url=$AWS_ENDPOINT_URL apigateway create-resource \
      --rest-api-id $API_ID --parent-id $ROOT_ID --path-part $ROUTE \
      --query 'id' --output text)
  aws --endpoint-url=$AWS_ENDPOINT_URL apigateway put-method \
      --rest-api-id $API_ID --resource-id $RES_ID --http-method GET \
      --authorization-type NONE
  aws --endpoint-url=$AWS_ENDPOINT_URL apigateway put-integration \
      --rest-api-id $API_ID --resource-id $RES_ID --http-method GET \
      --type AWS_PROXY --integration-http-method POST \
      --uri "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:000000000000:function:$FN/invocations"
  aws --endpoint-url=$AWS_ENDPOINT_URL lambda add-permission \
      --function-name $FN \
      --statement-id "apigw-$ROUTE" \
      --action lambda:InvokeFunction \
      --principal apigateway.amazonaws.com \
      --source-arn "arn:aws:execute-api:us-east-1:000000000000:$API_ID/*/GET/$ROUTE" || true
done

aws --endpoint-url=$AWS_ENDPOINT_URL apigateway create-deployment \
    --rest-api-id $API_ID --stage-name dev
```

## Step 5 — Frontend

```bash
cd ../s3/frontend
npm ci
cp .env.example .env
# Put the real API id in .env:
#   VITE_API_BASE_URL=http://<API_ID>.execute-api.localhost:4566/dev
sed -i "s|<API_ID>|$API_ID|" .env
npm run build

# Bucket `weather-app` already exists with its public-read policy — only the
# website config (idempotent) and the upload remain:
aws --endpoint-url=$AWS_ENDPOINT_URL s3 website s3://weather-app \
    --index-document index.html --error-document index.html
aws --endpoint-url=$AWS_ENDPOINT_URL s3 cp dist/ s3://weather-app/ --recursive
```

Open: <http://weather-app.s3-website.localhost:4566/index.html#/>

## Troubleshooting

- **Lambda can't reach Mongo/SQS** — `localhost` inside a containerized
  lambda is not the host. Use `host.docker.internal`; on plain Linux Docker
  fallback to `172.17.0.1` (docker bridge gateway).
- **CORS errors in the browser** — the REST API has
  `defaultCorsPreflightOptions` (allow `*`) and both lambdas set
  `Access-Control-Allow-Origin: *`. If a preflight still fails, verify the
  OPTIONS method exists: `aws apigateway get-resources --rest-api-id $API_ID`.
- **`checkstyle_supression.xml`** — the (sic) misspelled name is referenced
  by `pom.xml`'s `<propertyExpansion>` and wired to the SuppressionFilter of
  `google-checkstyle.xml`. Keep it next to the pom; it is intentionally empty.
- **CloudFormation rejects `AWS::ApiGateway::*`** — use the fallback CLI
  creation above.
- **`mvn test` fails offline** — the stack imports lambdas with
  `Function.fromFunctionName` (no runtime lookup), so tests never contact
  ministack; failures mean the synth assertions regressed.