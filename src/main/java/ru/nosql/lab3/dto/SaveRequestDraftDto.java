package ru.nosql.lab3.dto;

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
