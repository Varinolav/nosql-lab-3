package ru.nosql.lab2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.MongoWriteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.nosql.lab2.domain.BookingRequest;
import ru.nosql.lab2.domain.Building;
import ru.nosql.lab2.domain.RequestStatus;
import ru.nosql.lab2.domain.RequestAction;
import ru.nosql.lab2.domain.Room;
import ru.nosql.lab2.dto.CreateRequestDto;
import ru.nosql.lab2.dto.CreateRoomDto;
import ru.nosql.lab2.dto.RejectRequestDto;
import ru.nosql.lab2.dto.RequestDto;
import ru.nosql.lab2.dto.TagCountDto;
import ru.nosql.lab2.exception.ConflictException;
import ru.nosql.lab2.exception.ValidationException;
import ru.nosql.lab2.repository.redis.RedisRequestDraftRepository;
import ru.nosql.lab2.service.BookingRequestService;
import ru.nosql.lab2.service.RoomService;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@SpringBootTest(properties = {
        "spring.mongodb.uri=${TEST_MONGODB_URI:mongodb://localhost:27017/lab_2_test?replicaSet=rs0&directConnection=true}"
})
class Lab1ApplicationTests {
    @Autowired MongoTemplate mongo;
    @Autowired RoomService rooms;
    @Autowired BookingRequestService requests;
    @Autowired ru.nosql.lab2.service.ProcessingRuleService rules;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean RedisRequestDraftRepository drafts;
    @MockitoBean CacheManager cacheManager;

    private final LocalDateTime start = LocalDateTime.of(2030, 1, 1, 10, 0);

    @Test
    void assignsRulesOnlyToPendingRequestsAndUpdatesAllLinkedHistories() {
        var rule = rules.create(new ru.nosql.lab2.dto.CreateProcessingRuleDto("Учебное занятие", "Убрать аудиторию"));
        var room = rooms.create(new CreateRoomDto(Building.GK, 999, 30));
        var first = createRequest(room.id());
        var second = createRequest(room.id());
        var assignment = new ru.nosql.lab2.dto.AssignProcessingRuleDto(rule.id());
        var assigned = requests.assignRule(first.id(), assignment);
        assertEquals(rule.id(), assigned.ruleId());
        assertEquals(RequestAction.RULE_ASSIGNED, assigned.history().get(assigned.history().size() - 1).action());
        assertEquals(assigned.history().size(), requests.assignRule(first.id(), assignment).history().size());
        requests.assignRule(second.id(), assignment);
        requests.reviseSharedRule(rule.id(), new ru.nosql.lab2.dto.ReviseProcessingRuleDto(
                "Учебные занятия", "Выключить оборудование", "Уточнили условия"));
        assertEquals("Выключить оборудование", rules.findById(rule.id()).description());
        for (String id : List.of(first.id(), second.id())) {
            var updated = requests.findById(id);
            assertEquals(RequestAction.RULE_REVISED, updated.history().get(updated.history().size() - 1).action());
        }
        requests.approve(first.id());
        assertThrows(ConflictException.class, () -> requests.assignRule(first.id(), assignment));
    }

    @BeforeEach
    void resetTestData() {
        assertTrue(mongo.getDb().getName().endsWith("_test"), "Use a dedicated *_test database");
        mongo.remove(new Query(), BookingRequest.class);
        mongo.remove(new Query(), Room.class);
        when(cacheManager.getCache(anyString())).thenReturn(new ConcurrentMapCache("availableRooms"));
    }

    @Test
    void persistsRoomsRequestsAndApprovalWithObjectIds() {
        var room = rooms.create(new CreateRoomDto(Building.GK, 101, 30));
        assertTrue(ObjectId.isValid(room.id()));
        assertNotNull(mongo.getCollection("rooms").find(new Document("_id", new ObjectId(room.id()))).first());
        var request = createRequest(room.id());
        assertTrue(ObjectId.isValid(request.id()));
        assertNotNull(mongo.getCollection("booking_requests")
                .find(new Document("_id", new ObjectId(request.id()))).first());
        assertEquals(room.id(), requests.findById(request.id()).roomId());
        assertEquals(RequestStatus.PENDING, requests.findById(request.id()).status());
        var pending = requests.findById(request.id());
        assertEquals(1, pending.history().size());
        assertEquals(RequestAction.CREATED, pending.history().get(0).action());
        assertEquals(pending.createdAt(), pending.history().get(0).occurredAt());
        assertEquals(pending.createdAt(), pending.updatedAt());
        var document = mongo.getCollection("booking_requests")
                .find(new Document("_id", new ObjectId(request.id()))).first();
        assertEquals("CREATED", document.getList("history", Document.class).get(0).getString("action"));
        verify(drafts).deleteById(42);
        requests.approve(request.id());
        var stored = requests.findById(request.id());
        assertEquals(RequestStatus.APPROVED, stored.status());
        assertNotNull(stored.reviewedAt());
        assertEquals(List.of(RequestAction.CREATED, RequestAction.APPROVED),
                stored.history().stream().map(entry -> entry.action()).toList());
        assertEquals(stored.reviewedAt(), stored.updatedAt());
        assertEquals(stored.reviewedAt(), stored.history().get(1).occurredAt());
        assertNull(stored.history().get(1).comment());
        assertEquals(1, requests.findAllByStatus(RequestStatus.APPROVED).size());
        assertTrue(rooms.findAvailable(start, start.plusHours(1), 10).isEmpty());
        assertEquals(1, rooms.findAvailable(start.plusHours(1), start.plusHours(2), 10).size());
        rooms.delete(room.id());
        assertTrue(rooms.findAllActive().isEmpty());
        assertEquals(room.id(), requests.findById(request.id()).roomId());
    }

    @Test
    void persistsRejectionAndPreventsRepeatedReview() {
        var room = rooms.create(new CreateRoomDto(Building.LOMO, 102, 30));
        var request = createRequest(room.id());
        requests.reject(request.id(), new RejectRequestDto("Занятие отменено"));
        var stored = requests.findById(request.id());
        assertEquals(RequestStatus.REJECTED, stored.status());
        assertEquals("Занятие отменено", stored.reviewerComment());
        assertEquals(List.of(RequestAction.CREATED, RequestAction.REJECTED),
                stored.history().stream().map(entry -> entry.action()).toList());
        assertEquals(stored.reviewerComment(), stored.history().get(1).comment());
        assertEquals(stored.reviewedAt(), stored.updatedAt());
        assertThrows(ConflictException.class, () -> requests.approve(request.id()));
        assertEquals(RequestStatus.REJECTED, requests.findById(request.id()).status());
        assertEquals(stored.history(), requests.findById(request.id()).history());
    }

    @Test
    void rollsBackConflictingApproval() {
        var room = rooms.create(new CreateRoomDto(Building.GK, 103, 30));
        var first = createRequest(room.id());
        var second = createRequest(room.id());
        var before = requests.findById(second.id());
        requests.approve(first.id());
        assertThrows(ConflictException.class, () -> requests.approve(second.id()));
        assertEquals(RequestStatus.PENDING, requests.findById(second.id()).status());
        assertNull(requests.findById(second.id()).reviewedAt());
        assertEquals(before.history(), requests.findById(second.id()).history());
        assertEquals(before.updatedAt(), requests.findById(second.id()).updatedAt());
    }

    @Test
    void rollsBackHistoryTogetherWithStatus() {
        var room = rooms.create(new CreateRoomDto(Building.GK, 105, 30));
        var request = createRequest(room.id());
        var before = requests.findById(request.id());
        var transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            requests.approve(request.id());
            assertEquals(2, requests.findById(request.id()).history().size());
            throw new IllegalStateException("Force rollback after approval was saved");
        }));

        var after = requests.findById(request.id());
        assertEquals(RequestStatus.PENDING, after.status());
        assertNull(after.reviewedAt());
        assertEquals(before.history(), after.history());
        assertEquals(before.updatedAt(), after.updatedAt());
    }

    @Test
    void processesExistingRequestWithoutHistory() {
        var room = rooms.create(new CreateRoomDto(Building.GK, 106, 30));
        var request = createRequest(room.id());
        mongo.updateFirst(new Query(where("_id").is(request.id())),
                new Update().unset("history").unset("updatedAt"), BookingRequest.class);

        assertTrue(requests.findById(request.id()).history().isEmpty());
        requests.approve(request.id());
        var stored = requests.findById(request.id());
        assertEquals(1, stored.history().size());
        assertEquals(RequestAction.APPROVED, stored.history().get(0).action());
        assertEquals(stored.reviewedAt(), stored.updatedAt());
    }

    @Test
    void concurrentApprovalsCannotDoubleBookRoom() throws Exception {
        var room = rooms.create(new CreateRoomDto(Building.GK, 104, 30));
        var first = createRequest(room.id());
        var second = createRequest(room.id());
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> approveConcurrently(first.id(), ready, go));
            var b = executor.submit(() -> approveConcurrently(second.id(), ready, go));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(1, (a.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (b.get(20, TimeUnit.SECONDS) ? 1 : 0));
            assertEquals(1, requests.findAllByStatus(RequestStatus.APPROVED).size());
            assertEquals(1, requests.findAllByStatus(RequestStatus.PENDING).size());
            var approved = requests.findAllByStatus(RequestStatus.APPROVED).get(0);
            var pending = requests.findAllByStatus(RequestStatus.PENDING).get(0);
            assertEquals(2, approved.history().size());
            assertEquals(RequestAction.APPROVED, approved.history().get(1).action());
            assertEquals(1, pending.history().size());
            assertEquals(RequestAction.CREATED, pending.history().get(0).action());
        } finally {
            go.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void searchesByRoomAndUpdatedAtWithInclusiveBoundsAndStatus() {
        var room = rooms.create(new CreateRoomDto(Building.GK, 107, 30));
        var otherRoom = rooms.create(new CreateRoomDto(Building.GK, 108, 30));
        var lower = createRequest(room.id());
        var upper = createRequest(room.id());
        var outside = createRequest(room.id());
        var unrelated = createRequest(otherRoom.id());
        requests.reject(upper.id(), new RejectRequestDto("Отменено"));

        Instant from = Instant.parse("2030-02-01T00:00:00Z");
        Instant to = Instant.parse("2030-02-02T00:00:00Z");
        setUpdatedAt(lower.id(), from);
        setUpdatedAt(upper.id(), to);
        setUpdatedAt(outside.id(), from.minusMillis(1));
        setUpdatedAt(unrelated.id(), from);

        assertEquals(List.of(upper.id(), lower.id()), requests.search(room.id(), from, to, null)
                .stream().map(RequestDto::id).toList());
        assertEquals(List.of(upper.id()), requests.search(room.id(), from, to, RequestStatus.REJECTED)
                .stream().map(RequestDto::id).toList());
        assertTrue(requests.search(new ObjectId().toHexString(), from, to, null).isEmpty());
    }

    @Test
    void rejectsIncompleteAndReversedSearchPeriod() {
        Instant from = Instant.parse("2030-02-01T00:00:00Z");
        Instant to = from.plusSeconds(3600);
        assertThrows(ValidationException.class, () -> requests.search(null, from, to, null));
        assertThrows(ValidationException.class, () -> requests.search("room", null, to, null));
        assertThrows(ValidationException.class, () -> requests.search("room", from, null, null));
        assertThrows(ValidationException.class, () -> requests.search("room", to, from, null));
    }

    @Test
    void persistsNormalizedOptionalTagsAndKeepsThemAfterReview() {
        var room = rooms.create(new CreateRoomDto(Building.GK, 109, 30));
        var tagged = requests.create(new CreateRequestDto(42, room.id(), start, start.plusHours(1),
                "Практика", 10, List.of("  Учёба  ", "JAVA", "учёба", " ")));
        assertEquals(List.of("учёба", "java"), tagged.tags());
        assertEquals(tagged.tags(), requests.findById(tagged.id()).tags());
        assertEquals(tagged.tags(), mongo.getCollection("booking_requests")
                .find(new Document("_id", new ObjectId(tagged.id()))).first().getList("tags", String.class));
        assertEquals(tagged.tags(), requests.reject(tagged.id(), new RejectRequestDto("Отменено")).tags());

        assertTrue(createRequest(room.id()).tags().isEmpty());
        var blank = requests.create(new CreateRequestDto(42, room.id(), start, start.plusHours(1),
                "Практика", 10, List.of("   ")));
        assertTrue(requests.findById(blank.id()).tags().isEmpty());
    }

    @Test
    void aggregatesTagsByFrequencyAcrossStatusesAndBreaksTiesByName() {
        var room = rooms.create(new CreateRoomDto(Building.GK, 110, 30));
        for (String tag : List.of("  Учёба  ", "учёба", "Практика", "практика", "встреча", "   ")) {
            var request = requests.create(new CreateRequestDto(42, room.id(), start, start.plusHours(1),
                    "Практика", 10, List.of(tag)));
            if ("учёба".equals(tag)) requests.reject(request.id(), new RejectRequestDto("Отменено"));
        }
        var untagged = createRequest(room.id());
        // Emulate a document created before validation and the tags migration.
        mongo.getCollection("booking_requests").updateOne(new Document("_id", new ObjectId(untagged.id())),
                new Document("$unset", new Document("tags", "")),
                new com.mongodb.client.model.UpdateOptions().bypassDocumentValidation(true));
        assertEquals(List.of(new TagCountDto("практика", 2), new TagCountDto("учёба", 2),
                new TagCountDto("встреча", 1)), requests.findPopularTags());
    }

    @Test
    void returnsEmptyTagStatisticsWithoutTaggedRequests() {
        assertTrue(requests.findPopularTags().isEmpty());
        var room = rooms.create(new CreateRoomDto(Building.GK, 111, 30));
        createRequest(room.id());
        assertTrue(requests.findPopularTags().isEmpty());
    }

    @Test
    void countsEachTagOncePerRequestAcrossMultipleTags() {
        var room = rooms.create(new CreateRoomDto(Building.GK, 112, 30));
        var first = requests.create(new CreateRequestDto(42, room.id(), start, start.plusHours(1),
                "Практика", 10, List.of("java", "учёба")));
        requests.create(new CreateRequestDto(42, room.id(), start, start.plusHours(1),
                "Практика", 10, List.of("python", "учёба")));
        // Imported arrays may contain duplicates; analytics still counts requests.
        mongo.updateFirst(new Query(where("_id").is(first.id())),
                new Update().set("tags", List.of("java", "java", "учёба")), BookingRequest.class);
        assertEquals(List.of(new TagCountDto("учёба", 2), new TagCountDto("java", 1),
                new TagCountDto("python", 1)), requests.findPopularTags());
    }

    @Test
    void acceptsTagsArrayAndOptionalTagsInCreateDto() {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        assertEquals(List.of("учёба", "java"), json.readValue("{\"attendeesCount\":10,\"tags\":[\"учёба\",\"java\"]}", CreateRequestDto.class).tags());
        assertNull(json.readValue("{\"attendeesCount\":10}", CreateRequestDto.class).tags());
    }

    @Test
    void rejectsInvalidRoomsAtDatabaseLevel() {
        var collection = mongo.getCollection("rooms");
        assertSchemaRejection(() -> collection.insertOne(new Document("building", "GK")
                .append("number", 113).append("capacity", "30").append("active", true)));
        assertSchemaRejection(() -> collection.insertOne(new Document("building", "UNKNOWN")
                .append("number", 113).append("capacity", 30).append("active", true)));
        var room = rooms.create(new CreateRoomDto(Building.GK, 113, 30));
        var id = new Document("_id", new ObjectId(room.id()));
        assertSchemaRejection(() -> collection.updateOne(id, new Document("$set", new Document("capacity", 0))));
        assertSchemaRejection(() -> collection.updateOne(id, new Document("$unset", new Document("building", ""))));
        assertEquals(30, collection.find(id).first().getInteger("capacity"));
    }

    @Test
    void rejectsInvalidRequestsAndNestedFieldsAtDatabaseLevel() {
        var room = rooms.create(new CreateRoomDto(Building.GK, 114, 30));
        var request = createRequest(room.id());
        var collection = mongo.getCollection("booking_requests");
        var id = new Document("_id", new ObjectId(request.id()));
        List<Document> invalidFields = List.of(
                new Document("status", "UNKNOWN"),
                new Document("attendeesCount", 0),
                new Document("startsAt", "2030-01-01"),
                new Document("roomId", "not-an-object-id"),
                new Document("tags", "учёба"),
                new Document("tags", List.of(123)),
                new Document("tags", List.of("   ")),
                new Document("tag", "учёба"),
                new Document("reviewedAt", "yesterday"),
                new Document("reviewerComment", 123),
                new Document("lockVersion", -1L),
                new Document("lockVersion", 0.5),
                new Document("history", List.of(new Document("action", "UNKNOWN").append("occurredAt", new java.util.Date()))),
                new Document("history", List.of(new Document("action", "CREATED").append("occurredAt", "yesterday"))),
                new Document("history", List.of(new Document("action", "CREATED")))
        );
        for (Document fields : invalidFields) {
            assertSchemaRejection(() -> collection.updateOne(id, new Document("$set", fields)));
        }
        assertSchemaRejection(() -> collection.updateOne(id, new Document("$unset", new Document("tags", ""))));
        collection.updateOne(id, new Document("$set", new Document("reviewedAt", null)
                .append("reviewerComment", null).append("lockVersion", 0)
                .append("history", List.of(new Document("action", "CREATED")
                        .append("occurredAt", new java.util.Date()).append("comment", null)))));
        collection.updateOne(id, new Document("$set", new Document("lockVersion", 1L)));
        assertEquals(1L, collection.find(id).first().getLong("lockVersion"));
        assertEquals(RequestStatus.PENDING, requests.findById(request.id()).status());
        assertTrue(requests.findById(request.id()).tags().isEmpty());
        for (String name : List.of("rooms", "booking_requests")) {
            var options = mongo.getDb().listCollections().filter(new Document("name", name))
                    .first().get("options", Document.class);
            assertEquals("strict", options.getString("validationLevel"));
            assertEquals("error", options.getString("validationAction"));
            assertTrue(options.get("validator", Document.class).containsKey("$jsonSchema"));
        }
    }

    private void assertSchemaRejection(Runnable operation) {
        var error = assertThrows(MongoWriteException.class, operation::run);
        assertEquals(121, error.getError().getCode());
    }

    private void setUpdatedAt(String id, Instant value) {
        mongo.updateFirst(new Query(where("_id").is(id)), new Update().set("updatedAt", value), BookingRequest.class);
    }

    private boolean approveConcurrently(String id, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
        try {
            requests.approve(id);
            return true;
        } catch (ConflictException | DataIntegrityViolationException | TransientDataAccessException expected) {
            return false;
        }
    }

    private RequestDto createRequest(String roomId) {
        return requests.create(new CreateRequestDto(42, roomId, start, start.plusHours(1), "Практика", 10, null));
    }
}
