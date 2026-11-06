package ru.nosql.lab3.repository.mongo;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import ru.nosql.lab3.domain.BookingRequest;
import ru.nosql.lab3.dto.RequestSearchQuery;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BookingRequestSearchRepository {
    private final MongoTemplate mongo;

    public long count(RequestSearchQuery filters) {
        return mongo.count(query(filters), BookingRequest.class);
    }

    public List<BookingRequest> find(RequestSearchQuery filters, Sort sort) {
        Query query = query(filters)
                .with(sort)
                .skip((long) filters.page() * filters.size())
                .limit(filters.size());
        return mongo.find(query, BookingRequest.class);
    }

    private Query query(RequestSearchQuery filters) {
        List<Criteria> conditions = new ArrayList<>();
        if (filters.roomId() != null && !filters.roomId().isBlank()) {
            conditions.add(Criteria.where("roomId").is(filters.roomId().strip()));
        }
        if (filters.ruleId() != null && !filters.ruleId().isBlank()) {
            conditions.add(Criteria.where("ruleId").is(filters.ruleId().strip()));
        }
        if (filters.userId() != null) {
            conditions.add(Criteria.where("userId").is(filters.userId()));
        }
        if (filters.status() != null) {
            conditions.add(Criteria.where("status").is(filters.status()));
        }
        if (filters.historyAction() != null) {
            conditions.add(Criteria.where("history.action").is(filters.historyAction()));
        }
        if (filters.updatedFrom() != null || filters.updatedTo() != null) {
            Criteria updatedAt = Criteria.where("updatedAt");
            if (filters.updatedFrom() != null) {
                updatedAt.gte(filters.updatedFrom());
            }
            if (filters.updatedTo() != null) {
                updatedAt.lte(filters.updatedTo());
            }
            conditions.add(updatedAt);
        }
        return conditions.isEmpty()
                ? new Query()
                : new Query(new Criteria().andOperator(conditions.toArray(new Criteria[0])));
    }
}
