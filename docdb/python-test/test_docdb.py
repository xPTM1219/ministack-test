"""
Basic DocumentDB (Mongo-compatible) smoke test against the CDK-deployed cluster.

Usage:
    export MONGO_URI="mongodb://docdbadmin:<password>@<endpoint>:27017"
    python test_docdb.py
"""

import os
from datetime import datetime, timezone

import pymongo

uri = os.environ.get("MONGO_URI", "mongodb://docdbadmin:password@localhost:27017")

client = pymongo.MongoClient(uri, serverSelectionTimeoutMS=5000)

collection = client["test"]["docdb_test"]
collection.drop()

inserted = collection.insert_one(
    {
        "_id": 1,
        "title": "test from cdk",
        "ts": datetime.now(timezone.utc).isoformat(),
    }
)
print(f"Inserted _id={inserted.inserted_id}")

all_docs = list(collection.find({}))
print("Find all:")
for doc in all_docs:
    print(doc)

one_doc = collection.find_one({"_id": 1})
print("Find one:", one_doc)

result = collection.delete_one({"_id": 1})
print("Deleted count:", result.deleted_count)

client.close()

if result.deleted_count == 1 and one_doc is not None and len(all_docs) == 1:
    print("All ops succeeded")
else:
    raise SystemExit("Ops did not complete as expected")
