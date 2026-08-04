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
    """Lambda for listing documents via API Gateway (GET)"""
    try:
        # Support query params for limit etc, simple list all
        limit = 100
        if event.get('queryStringParameters') and event['queryStringParameters'].get('limit'):
            limit = int(event['queryStringParameters']['limit'])

        docs = list(collection.find({}, {'_id': 0}).limit(limit))  # exclude _id or convert if needed
        # If you want _id, do: str(doc['_id']) etc, here omitted for simplicity

        return {
            'statusCode': 200,
            'headers': {'Content-Type': 'application/json'},
            'body': json.dumps({
                'count': len(docs),
                'items': docs
            })
        }
    except Exception as e:
        return {
            'statusCode': 500,
            'body': json.dumps({'error': str(e)})
        }
