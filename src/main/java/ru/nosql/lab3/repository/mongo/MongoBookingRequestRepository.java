package ru.nosql.lab3.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Aggregation;
import ru.nosql.lab3.domain.BookingRequest;
import ru.nosql.lab3.domain.RequestStatus;
import ru.nosql.lab3.dto.TagCountDto;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

public interface MongoBookingRequestRepository
        extends MongoRepository<BookingRequest, String> {

    List<BookingRequest> findAllByOrderByCreatedAtAscIdAsc();

    List<BookingRequest> findAllByRuleIdOrderByIdAsc(String ruleId);

    List<BookingRequest> findAllByStatusOrderByCreatedAtAscIdAsc(RequestStatus status);

    @Aggregation(pipeline = {
            "{'$project': {'tags': {'$setUnion': [{'$ifNull': ['$tags', []]}, []]}}}",
            "{'$unwind': '$tags'}",
            "{'$match': {'tags': {'$type': 'string', '$ne': ''}}}",
            "{'$group': {'_id': '$tags', 'count': {'$sum': 1}}}",
            "{'$sort': {'count': -1, '_id': 1}}",
            "{'$project': {'_id': 0, 'tag': '$_id', 'count': 1}}"
    })
    List<TagCountDto> findPopularTags();

    @Query(value = "{'roomId': ?0, 'updatedAt': {'$gte': ?1, '$lte': ?2}}", sort = "{'updatedAt': -1}")
    List<BookingRequest> findByRoomAndUpdatedAt(
            String roomId, Instant updatedFrom, Instant updatedTo);

    @Query(value = "{'roomId': ?0, 'status': ?1, 'updatedAt': {'$gte': ?2, '$lte': ?3}}", sort = "{'updatedAt': -1}")
    List<BookingRequest> findByRoomAndStatusAndUpdatedAt(
            String roomId, RequestStatus status, Instant updatedFrom, Instant updatedTo);

    boolean existsByRoomIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            String roomId, RequestStatus status, LocalDateTime endsAt, LocalDateTime startsAt);

    default boolean existsOverlapping(String roomId, RequestStatus status,
                                      LocalDateTime startsAt, LocalDateTime endsAt) {
        return existsByRoomIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                roomId, status, endsAt, startsAt);
    }
}
