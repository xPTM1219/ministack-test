package com.example;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.time.Instant;
import org.bson.Document;

/**
 * Basic DocumentDB (Mongo-compatible) smoke test against the CDK-deployed cluster.
 *
 * Usage: MONGO_URI="mongodb://docdbadmin:<password>@<endpoint>:27017" mvn compile exec:java
 */
public final class DocdbTest {
    private DocdbTest() {
    }

    public static void main(final String[] args) {
        String uri = System.getenv().getOrDefault(
                "MONGO_URI", "mongodb://docdbadmin:password@localhost:27017");

        try (MongoClient client = MongoClients.create(uri)) {
            MongoDatabase database = client.getDatabase("test");
            MongoCollection<Document> collection = database.getCollection("docdb_test");
            collection.drop();

            Document doc = new Document("_id", 1)
                    .append("title", "test from cdk")
                    .append("ts", Instant.now().toString());

            collection.insertOne(doc);
            System.out.println("Inserted _id=" + doc.get("_id"));

            System.out.println("Find all:");
            for (Document d : collection.find()) {
                System.out.println(d.toJson());
            }

            Document one = collection.find(new Document("_id", 1)).first();
            System.out.println("Find one: " + (one != null ? one.toJson() : null));

            long deleted = collection.deleteOne(new Document("_id", 1)).getDeletedCount();
            System.out.println("Deleted count: " + deleted);

            if (deleted == 1 && one != null) {
                System.out.println("All ops succeeded");
            } else {
                throw new IllegalStateException("Ops did not complete as expected");
            }
        }
    }
}
