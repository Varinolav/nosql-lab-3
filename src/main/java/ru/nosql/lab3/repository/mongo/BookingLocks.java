package ru.nosql.lab3.repository.mongo;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import ru.nosql.lab3.domain.BookingRequest;
import ru.nosql.lab3.domain.ProcessingRule;
import ru.nosql.lab3.domain.Room;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Component
@RequiredArgsConstructor
public class BookingLocks {
    private final MongoTemplate mongo;

    public void lockRoom(String id) {
        mongo.updateFirst(new Query(where("_id").is(id)),
                new Update().inc("lockVersion", 1L), Room.class);
    }

    public void lockRequest(String id) {
        mongo.updateFirst(new Query(where("_id").is(id)),
                new Update().inc("lockVersion", 1L), BookingRequest.class);
    }

    public void lockRule(String id) {
        mongo.updateFirst(new Query(where("_id").is(id)),
                new Update().inc("lockVersion", 1L), ProcessingRule.class);
    }
}
