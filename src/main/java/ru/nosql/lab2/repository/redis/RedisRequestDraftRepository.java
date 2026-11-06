package ru.nosql.lab2.repository.redis;

import org.springframework.data.repository.CrudRepository;
import ru.nosql.lab2.domain.RequestDraft;

public interface RedisRequestDraftRepository extends CrudRepository<RequestDraft, Integer> {
}
