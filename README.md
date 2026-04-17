# LocalStack S3-Lambda-SQS Prototype

This project demonstrates a workflow using LocalStack (free version) to integrate S3, Lambda, and SQS.

## Setup

1. Start LocalStack:
   ```bash
   docker run --rm -d --name localstack -p 4566:4566 -p 4572:4572 localstack/localstack
   ```

2. Install dependencies:
   - AWS CLI v2
   - Python packages: `boto3`, `Pillow`

3. Create resources:
   ```bash
   export AWS_ACCESS_KEY_ID=test
   export AWS_SECRET_ACCESS_KEY=test
   export AWS_REGION=us-east-1

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
   python process_image.py
   ```

3. Invoke the Lambda function to poll SQS and display metadata:
   ```bash
   aws --endpoint-url=http://localhost:4566 lambda invoke --function-name DisplayMetadata output.json
   ```

The Lambda will print the extracted EXIF metadata from the image.

## Troubleshooting

I created a ticket with Ministack to report the error I got with Lambda [Python module boto3 not found when invoking a Lambda function](https://github.com/ministackorg/ministack/issues/362?reload=1)
