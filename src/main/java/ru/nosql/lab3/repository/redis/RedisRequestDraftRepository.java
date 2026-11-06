package ru.nosql.lab3.repository.redis;

import org.springframework.data.repository.CrudRepository;
import ru.nosql.lab3.domain.RequestDraft;

public interface RedisRequestDraftRepository extends CrudRepository<RequestDraft, Integer> {
}
