package ru.nosql.lab2.dto;

import java.time.Instant;
import java.time.LocalDateTime;
public record RequestDraftDto(
        Integer userId,
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
