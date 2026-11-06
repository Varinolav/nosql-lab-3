package ru.nosql.lab2.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.schema.JsonSchemaObject;
import org.springframework.data.mongodb.core.schema.JsonSchemaProperty;
import org.springframework.data.mongodb.core.schema.MongoJsonSchema;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import ru.nosql.lab2.domain.Room;
import ru.nosql.lab2.domain.BookingRequest;
import ru.nosql.lab2.domain.ProcessingRule;

import java.util.List;

import static org.springframework.data.mongodb.core.schema.JsonSchemaProperty.*;

@Configuration
@EnableTransactionManagement
public class MongoConfig {
    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Bean
    InitializingBean mongoCollections(MongoTemplate mongo, LegacyRequestTagsMigration tagsMigration) {
        return () -> {
            mongo.indexOps(Room.class).createIndex(new Index()
                    .on("building", Sort.Direction.ASC).on("number", Sort.Direction.ASC).unique());
            mongo.indexOps(BookingRequest.class).createIndex(new Index()
                    .on("roomId", Sort.Direction.ASC).on("updatedAt", Sort.Direction.DESC)
                    .named("idx_requests_room_updated"));
            mongo.indexOps(BookingRequest.class).createIndex(new Index()
                    .on("ruleId", Sort.Direction.ASC).on("updatedAt", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
                    .named("idx_requests_rule_updated_id"));
            mongo.indexOps(ProcessingRule.class).createIndex(new Index()
                    .on("name", Sort.Direction.ASC).named("idx_rules_name"));
            configureValidation(mongo, "rooms", roomsSchema());
            long migrated = tagsMigration.migrate();
            configureValidation(mongo, "booking_requests", requestsSchema());
            configureValidation(mongo, "processing_rules", rulesSchema());
        };
    }

    private MongoJsonSchema roomsSchema() {
        return MongoJsonSchema.builder()
                .required("_id", "building", "number", "capacity", "active")
                .additionalProperties(false)
                .properties(
                        objectId("_id"),
                        string("building").possibleValues("GK", "LOMO"),
                        int32("number").gte(1),
                        int32("capacity").gte(1),
                        bool("active"),
                        lockVersion(),
                        string("_class"))
                .build();
    }

    private MongoJsonSchema requestsSchema() {
        var historyEntry = JsonSchemaObject.object()
                .required("action", "occurredAt")
                .additionalProperties(false)
                .properties(
                        string("action").possibleValues("CREATED", "APPROVED", "REJECTED", "RULE_REVISED", "RULE_ASSIGNED"),
                        date("occurredAt"),
                        untyped("comment").anyOf(List.of(JsonSchemaObject.string(), JsonSchemaObject.nil())));

        return MongoJsonSchema.builder()
                .required("_id", "userId", "roomId", "startsAt", "endsAt", "purpose",
                        "attendeesCount", "status", "createdAt", "tags")
                .additionalProperties(false)
                .properties(
                        objectId("_id"),
                        int32("userId"),
                        string("roomId").matching("^[0-9a-fA-F]{24}$"),
                        untyped("ruleId").anyOf(List.of(
                                JsonSchemaObject.string().matching("^[0-9a-fA-F]{24}$"), JsonSchemaObject.nil())),
                        date("startsAt"),
                        date("endsAt"),
                        string("purpose").matching("\\S"),
                        int32("attendeesCount").gte(1),
                        string("status").possibleValues("PENDING", "APPROVED", "REJECTED"),
                        date("createdAt"),
                        untyped("reviewedAt").anyOf(List.of(JsonSchemaObject.date(), JsonSchemaObject.nil())),
                        untyped("reviewerComment").anyOf(List.of(JsonSchemaObject.string(), JsonSchemaObject.nil())),
                        date("updatedAt"),
                        array("history").items(historyEntry),
                        array("tags").items(JsonSchemaObject.string().matching("\\S")),
                        lockVersion(),
                        string("_class"))
                .build();
    }

    private MongoJsonSchema rulesSchema() {
        return MongoJsonSchema.builder()
                .required("_id", "name", "description", "active", "createdAt", "updatedAt")
                .additionalProperties(false)
                .properties(
                        objectId("_id"),
                        string("name").matching("\\S"),
                        string("description"),
                        bool("active"),
                        date("createdAt"),
                        date("updatedAt"),
                        lockVersion(),
                        string("_class"))
                .build();
    }

    private JsonSchemaProperty lockVersion() {
        return untyped("lockVersion")
                .anyOf(List.of(JsonSchemaObject.of(Integer.class), JsonSchemaObject.of(Long.class)))
                .allOf(List.of(JsonSchemaObject.number().gte(0)));
    }

    private void configureValidation(MongoTemplate mongo, String collection, MongoJsonSchema schema) {
        mongo.executeCommand(new Document("collMod", collection)
                .append("validator", schema.toDocument())
                .append("validationLevel", "strict")
                .append("validationAction", "error"));
    }
}
