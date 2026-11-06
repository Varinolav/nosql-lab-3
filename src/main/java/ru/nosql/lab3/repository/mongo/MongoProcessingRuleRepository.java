package ru.nosql.lab3.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.nosql.lab3.domain.ProcessingRule;

import java.util.List;

public interface MongoProcessingRuleRepository extends MongoRepository<ProcessingRule, String> {
    List<ProcessingRule> findAllByOrderByNameAscIdAsc();
}
