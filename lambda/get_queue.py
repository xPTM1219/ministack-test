"""GetQueue lambda: return SQS messages from a queue as JSON.

Receives messages (without deleting them) from the configured SQS queue and
returns them as an API Gateway proxy response. Message bodies are parsed as
JSON when possible, otherwise returned as raw strings.

Env vars: SQS_ENDPOINT_URL (default http://host.docker.internal:4566),
QUEUE_URL (default test-queue URL), MAX_MESSAGES (default 50).
"""

import json
import os

import boto3

SQS_ENDPOINT_URL = os.environ.get('SQS_ENDPOINT_URL', 'http://host.docker.internal:4566')
QUEUE_URL = os.environ.get(
    'QUEUE_URL',
    'http://sqs.us-east-1.localhost.ministack.cloud:4566/000000000000/test-queue',
)
MAX_MESSAGES = int(os.environ.get('MAX_MESSAGES', '50'))

CORS_HEADERS = {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
}


def receive_messages(sqs, queue_url, max_messages):
    """Receive up to `max_messages` messages without deleting them.

    :param sqs: boto3 SQS client.
    :param queue_url: full URL of the queue to read.
    :param max_messages: maximum number of messages to return.
    :returns: list of raw SQS message dicts.
    """
    messages = []
    while len(messages) < max_messages:
        response = sqs.receive_message(
            QueueUrl=queue_url,
            AttributeNames=['All'],
            MessageAttributeNames=['.*'],
            MaxNumberOfMessages=min(10, max_messages - len(messages)),
            VisibilityTimeout=1,
        )
        batch = response.get('Messages', [])
        if not batch:
            break
        messages.extend(batch)
    return messages


def serialize_messages(messages):
    """Map raw SQS messages to JSON-friendly dicts with parsed bodies.

    :param messages: list of raw SQS message dicts.
    :returns: list of {'messageId', 'body', 'receiptHandle'} dicts; `body` is a
              JSON value when parseable, else the raw string.
    """
    result = []
    for message in messages:
        body = message.get('Body', '')
        try:
            body = json.loads(body)
        except (ValueError, TypeError):
            pass
        result.append({
            'messageId': message.get('MessageId'),
            'body': body,
            'receiptHandle': message.get('ReceiptHandle'),
        })
    return result


def lambda_handler(event, context):
    """Lambda entry point: return current SQS queue messages as JSON."""
    try:
        sqs = boto3.client(
            'sqs',
            endpoint_url=SQS_ENDPOINT_URL,
            aws_access_key_id='test',
            aws_secret_access_key='test',
            region_name='us-east-1',
        )
        messages = receive_messages(sqs, QUEUE_URL, MAX_MESSAGES)
        body = {'messages': serialize_messages(messages)}
        return {
            'statusCode': 200,
            'headers': CORS_HEADERS,
            'body': json.dumps(body),
        }
    except Exception as e:  # noqa: BLE001 - any failure becomes a 500 response
        return {
            'statusCode': 500,
            'headers': CORS_HEADERS,
            'body': json.dumps({'error': str(e)}),
        }