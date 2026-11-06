package ru.nosql.lab2.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.Instant;
import java.time.LocalDateTime;
@RedisHash(value = "request_drafts", timeToLive = 1800)
public record RequestDraft(
        @Id Integer userId,
        String roomId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String purpose,
        Integer attendeesCount,
        Instant updatedAt,
        Instant expiresAt,
        String tag
) {
}
