import boto3
from PIL import Image
import json
from io import BytesIO

# Configure boto3 clients for LocalStack
s3 = boto3.client(
    's3',
    endpoint_url='http://localhost:4566',
    aws_access_key_id='test',
    aws_secret_access_key='test',
    region_name='us-east-1'
)

sqs = boto3.client(
    'sqs',
    endpoint_url='http://localhost:4566',
    aws_access_key_id='test',
    aws_secret_access_key='test',
    region_name='us-east-1'
)

# Bucket and queue details
bucket_name = 'test-bucket'
object_key = 'tof-valkyrie.jpg'  # Assumes the uploaded image is named 'image.jpg'
queue_url = 'http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/test-queue'

def extract_metadata():
    try:
        # Fetch the image from S3
        response = s3.get_object(Bucket=bucket_name, Key=object_key)
        image_data = response['Body'].read()
        # print(response)

        # Open the image with PIL
        image = Image.open(BytesIO(image_data))
        image.load()

        print("Image format:", image.format)
        print("Size:", image.size)
        print("All:", image.getdata())

        # Extract EXIF metadata
        exif_data = image._getexif()
        exif = {}
        
        if exif_data:
            for tag_id, value in exif_data.items():
                tag_name = Image.ExifTags.TAGS.get(tag_id, tag_id)
                exif[tag_name] = value
        print(exif)
        if exif_data:
            metadata = {Image.ExifTags.TAGS.get(k, k): v for k, v in exif_data.items()}
        else:
            metadata = {"message": "No EXIF data found"}

        exit()
        return metadata

    except Exception as e:
        return {"error": str(e)}

def send_to_sqs(metadata):
    try:
        message_body = json.dumps(metadata)
        response = sqs.send_message(QueueUrl=queue_url, MessageBody=message_body)
        print(f"Message sent to SQS. MessageId: {response['MessageId']}")
    except Exception as e:
        print(f"Error sending message to SQS: {e}")

if __name__ == "__main__":
    metadata = extract_metadata()
    print("Extracted metadata:", metadata)
    send_to_sqs(metadata)