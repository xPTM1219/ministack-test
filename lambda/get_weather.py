"""GetWeather lambda: last 3 days of weather from MongoDB, aggregated hourly.

Reads all documents from `xtemp.weather` (configurable via env vars), merges
the `weather` maps of every document by date key (`YYYY-MM-DD` -> `HH:MM:SS`
-> `{description, temp}`), keeps the last 3 dates and groups each day's
samples by hour, averaging the temperature and taking the most frequent
description per hour. Temps are stored as strings, so they are coerced to
floats with skip-on-error semantics.

API Gateway proxy response body shape::

    {"dates": ["2026-01-12", "2026-01-11", "2026-01-10"],
     "weather": {"2026-01-12": [{"hour": "18:00", "description": "clear sky",
                                 "temp": 35.2}, ...]}}
"""

import json
import os
from collections import Counter
from math import isnan

from pymongo import MongoClient

MONGO_URI = os.environ.get('MONGO_URI', 'mongodb://localhost:27017/')
DB_NAME = os.environ.get('DB_NAME', 'xtemp')
COLLECTION_NAME = os.environ.get('COLLECTION_NAME', 'weather')
MAX_DATES = 3

CORS_HEADERS = {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
}


def _parse_temp(value):
    """Coerce a temperature (string or number) to float, or None on failure."""
    try:
        temp = float(value)
        return None if isnan(temp) else temp
    except (TypeError, ValueError):
        return None


def collect_weather(collection):
    """Merge the `weather` maps of all documents into one dict keyed by date.

    :param collection: pymongo collection with documents holding a `weather` map.
    :returns: dict mapping date -> {timestamp -> {'description': str, 'temp': str}}
    """
    merged = {}
    for doc in collection.find({}, {'weather': 1}):
        merged.update(doc.get('weather') or {})
    return merged


def aggregate_hourly(samples):
    """Group one date's samples by hour, averaging temps per hour.

    :param samples: dict mapping `HH:MM:SS` -> {'description': str, 'temp': str}
    :returns: list of {'hour': 'HH:00', 'description': str, 'temp': float}
              sorted by hour.
    """
    hours = {}
    for timestamp, sample in samples.items():
        hour = timestamp.split(' ')[0][:2] if ' ' in timestamp else timestamp[:2]
        hour = f'{hour}:00'
        entry = hours.setdefault(hour, {'temps': [], 'descriptions': []})
        temp = _parse_temp(sample.get('temp'))
        if temp is not None:
            entry['temps'].append(temp)
        description = sample.get('description')
        if description:
            entry['descriptions'].append(description)

    result = []
    for hour in sorted(hours):
        entry = hours[hour]
        temps = entry['temps']
        descriptions = entry['descriptions']
        result.append({
            'hour': hour,
            'description': Counter(descriptions).most_common(1)[0][0] if descriptions else '',
            'temp': round(sum(temps) / len(temps), 2) if temps else None,
        })
    return result


def lambda_handler(event, context):
    """Lambda entry point: return hourly-aggregated weather for the last 3 days."""
    try:
        client = MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000)
        collection = client[DB_NAME][COLLECTION_NAME]

        merged = collect_weather(collection)
        dates = sorted(merged.keys(), reverse=True)[:MAX_DATES]

        body = {
            'dates': dates,
            'weather': {date: aggregate_hourly(merged[date]) for date in dates},
        }
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