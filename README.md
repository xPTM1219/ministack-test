# MiniStack S3-Lambda-SQS Prototype

This project demonstrates a workflow using Ministack to integrate different
AWS services.

## Structure

As you can see now, the project is structured in various folders that contains
AWS services. In each folder you will find different languages and
implementations. Ministack configuration still remains in root.

* Lambda - Deploys a lambda that reads SQS queue.
* ECS - Deploys two services of Vuejs and Nginx with CDK. Typescript and Java.
* EKS - Deploys a K8s cluster that host Apache and Alpine Linux nodes.

## Requirements

* python-pip, if on Linux
* zip or 7zip or WinRAR
* Activate the environment
  * `source venv/bin/activate` if using Bash
  * `source venv/bin/activate.fish` if usinf Fish
  * `venv\Scripts\activate.ps1` if usng Windows
* Python dependencies
  * Create virtual environment `python3 -m virtualenv venv`
  * `pip3 install -r requirements`
* AWS CLI v2
  * See their [docs](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)

## Setup

1. Start Ministack:
   ```bash
   docker run --rm -d --name ministack -p 4566:4566 -p 4572:4572 ministackorg/ministack
   ```

   or use the docker compose file

   ```bash
   docker compose up
   ```

2. Create resources:
   ```bash
   export AWS_ACCESS_KEY_ID=test
   export AWS_SECRET_ACCESS_KEY=test
   export AWS_REGION=us-east-1
   export MINISTACK_VERSION=1.4.9

   aws --endpoint-url=http://localhost:4566 s3 mb s3://test-bucket
   aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name test-queue
   ```

4. Create Lambda function:
   ```bash
   zip lambda_function.zip lambda_function.py
   aws --endpoint-url=http://localhost:4566 lambda create-function --function-name DisplayMetadata --runtime python3.9 --role arn:aws:iam::000000000000:role/lambda-role --handler lambda_function.lambda_handler --zip-file fileb://lambda_function.zip
   ```

## Workflow

1. Upload an image to S3:
   ```bash
   aws --endpoint-url=http://localhost:4566 s3 cp path/to/your/image.jpg s3://test-bucket/image.jpg
   ```

2. Run the Python script to extract metadata and send to SQS:
   ```bash
   # Process the image
   python process_image.py

   # Check if the queue is populated
   aws --endpoint-url=http://localhost:4566 sqs receive-message --queue-url http://sqs.us-east-1.localhost.ministack.cloud:4566/000000000000/test-queue
   ```

3. Invoke the Lambda function to poll SQS and display metadata:
   ```bash
   aws --endpoint-url=http://localhost:4566 lambda invoke --function-name DisplayMetadata output.json
   ```
4. The Lambda will print the extracted information in the terminal.
   Note that in the current status, the message is in base64,
   you need to decode it.

## Mongo + API Gateway + Lambda Example

This project now includes Lambdas that use API Gateway to insert/list docs in a local MongoDB.

### Quick start for Mongo example

1. Make sure docker compose includes mongo (added), run:
   ```bash
   ./prepare_ministack
   ```
   (or manually: docker compose up -d ; then the aws cli parts)

   Mongo will be at localhost:27017 , db=ministackdb , collection=items

2. The prepare script now also:
   - Packages the Lambdas with pymongo
   - Creates `InsertDocument` and `ListDocuments` functions (note: uses host.docker.internal for mongo conn from containerized Lambda)
   - Creates a REST API "MongoApi" with POST/GET /items

3. Invoke (replace $API_ID from script output):
   ```bash
   # Insert
   curl -X POST http://$API_ID.execute-api.localhost:4566/dev/items \
     -H "Content-Type: application/json" \
     -d '{"title": "Hello Ministack", "tags": ["aws", "local"]}'

   # List
   curl http://$API_ID.execute-api.localhost:4566/dev/items
   ```

Alternative invoke path (no custom dns):
   `curl http://localhost:4566/_aws/execute-api/$API_ID/dev/items`

The Lambda code is in `lambda/insert_document.py` and `lambda/list_documents.py`

You can test the mongo directly:
   ```bash
   python -c "
   from pymongo import MongoClient
   c = MongoClient('mongodb://localhost:27017/')
   print(list(c.ministackdb.items.find()))
   "
   ```

Note: If Lambda can't reach mongo, try changing MONGO_URI to 'mongodb://172.17.0.1:27017/' (docker host ip on linux) or run Lambda with LAMBDA_EXECUTOR=local.

## Troubleshooting

1. I created a ticket with Ministack to report the error I got with Lambda [Python module boto3 not found when invoking a Lambda function](https://github.com/ministackorg/ministack/issues/362?reload=1)
   * The ticket was resolved the next morning.
2. 

## Resources

* [Ministack blog](https://dev.to/nahuel990/free-Ministack-alternative-20-aws-services-and-counting-4ob1)
* [Ministack Github](https://github.com/ministackorg/ministack)
* [Ministack website](https://ministack.org/)
