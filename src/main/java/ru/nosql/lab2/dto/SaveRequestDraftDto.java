package ru.nosql.lab2.dto;

import java.time.LocalDateTime;
public record SaveRequestDraftDto(
        String roomId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String purpose,
        Integer attendeesCount,
        String tag
) {
}
