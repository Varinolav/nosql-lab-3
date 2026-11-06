package ru.nosql.lab2.config;

import com.mongodb.client.MongoCollection;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LegacyRequestTagsMigration {
    private static final Document NEEDS_MIGRATION = Document.parse("""
            { "$or": [
                { "tag": { "$exists": true } },
                { "tags": { "$not": { "$type": "array" } } }
            ] }
            """);

    private static final Document SET_TAGS = Document.parse("""
            { "$set": { "tags": { "$setUnion": [
                { "$cond": [ { "$isArray": "$tags" }, "$tags", [] ] },
                { "$cond": [
                    { "$eq": [ { "$type": "$tag" }, "string" ] },
                    { "$cond": [
                        { "$ne": [ { "$trim": { "input": "$tag" } }, "" ] },
                        [ { "$trim": { "input": "$tag" } } ],
                        []
                    ] },
                    []
                ] }
            ] } } }
            """);

    private final MongoTemplate mongo;

    public long migrate() {
        return requests().updateMany(NEEDS_MIGRATION,
                List.of(SET_TAGS, new Document("$unset", "tag"))).getModifiedCount();
    }

    private MongoCollection<Document> requests() {
        return mongo.getCollection("booking_requests");
    }
}
