import json
import sys
import os

# # Get the absolute path to your project's root or the venv site-packages
# project_dir = os.path.dirname(os.path.abspath(__file__))  # This gives you project_dir/
# # Or hardcode if needed: project_dir = "/absolute/path/to/project_dir"

# venv_site_packages = os.path.join(project_dir, "venv", "lib", "python3.12", "site-packages")

# # Add it to the front of sys.path so Python finds boto3 (and its dependencies) first
# if venv_site_packages not in sys.path:
#     sys.path.insert(0, venv_site_packages)  # insert(0) puts it at the highest priority

# Now you can import boto3 normally
import boto3

def lambda_handler(event, context):
    # Configure SQS client for LocalStack
    sqs = boto3.client(
        'sqs',
        endpoint_url='http://localhost:4566',
        aws_access_key_id='test',
        aws_secret_access_key='test',
        region_name='us-east-1'
    )

    queue_url = 'http://sqs.us-east-1.localhost.ministack.cloud:4566/000000000000/test-queue'

    try:
        # Receive messages from SQS
        # response = sqs.receive_message(
        #     QueueUrl=queue_url,
        #     AttributeNames=['All'],
        #     MessageAttributeNames=[".*"],
        #     MaxNumberOfMessages=10
        # )

        # messages = response.get('Messages', [])

        # if not messages:
        #     print("No messages in the queue.")
        #     return

        for message in get_messages_from_queue(sqs, queue_url):
            message_body = json.loads(message['Body'])
            print("Received metadata:", json.dumps(message_body, indent=2))

            # Delete the message after processing
            # sqs.delete_message(
            #     QueueUrl=queue_url,
            #     ReceiptHandle=message['ReceiptHandle']
            # )

    except Exception as e:
        print(f"Error processing messages: {e}")

def get_messages_from_queue(sqs, queue_url):
    """
    Generates messages from an SQS queue.
    Note: this continues to generate messages until the queue is empty.
    :param queue_url: URL of the SQS queue to drain.
    """
    while True:
        resp = sqs.receive_message(
            QueueUrl=queue_url,
            AttributeNames=['All'],
            MessageAttributeNames=[".*"],
            MaxNumberOfMessages=10
        )

        try:
            # returns an iterable object
            # makes this function a generator function
            yield from resp['Messages']
        except KeyError:
            print("Queue is empty")
            return
