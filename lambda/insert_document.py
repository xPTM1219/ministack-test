import json
import os
from pymongo import MongoClient

# MongoDB connection - use localhost for host python, host.docker.internal if Lambda runs in Docker
MONGO_URI = os.environ.get('MONGO_URI', 'mongodb://localhost:27017/')
DB_NAME = os.environ.get('DB_NAME', 'ministackdb')
COLLECTION_NAME = os.environ.get('COLLECTION_NAME', 'items')

client = MongoClient(MONGO_URI)
db = client[DB_NAME]
collection = db[COLLECTION_NAME]

def lambda_handler(event, context):
    """Lambda for inserting a document via API Gateway (POST)"""
    try:
        # API Gateway proxy integration: body is JSON string
        if isinstance(event.get('body'), str):
            body = json.loads(event['body'])
        else:
            body = event.get('body', event)  # fallback for direct invoke

        if not isinstance(body, dict):
            return {
                'statusCode': 400,
                'body': json.dumps({'error': 'Request body must be a JSON object'})
            }

        result = collection.insert_one(body)
        return {
            'statusCode': 201,
            'headers': {'Content-Type': 'application/json'},
            'body': json.dumps({
                'message': 'Document inserted',
                'inserted_id': str(result.inserted_id)
            })
        }
    except Exception as e:
        return {
            'statusCode': 500,
            'body': json.dumps({'error': str(e)})
        }
