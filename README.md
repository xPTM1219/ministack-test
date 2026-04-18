# MiniStack S3-Lambda-SQS Prototype

This project demonstrates a workflow using Ministack to integrate different
AWS services.

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
   export MINISTACK_VERSION=1.2.21

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

## Troubleshooting

1. I created a ticket with Ministack to report the error I got with Lambda [Python module boto3 not found when invoking a Lambda function](https://github.com/ministackorg/ministack/issues/362?reload=1)
   * The ticket was resolved the next morning.
2. 

## Resources

* [Ministack blog](https://dev.to/nahuel990/free-Ministack-alternative-20-aws-services-and-counting-4ob1)
* [Ministack Github](https://github.com/ministackorg/ministack)
* [Ministack website](https://ministack.org/)
