package ru.nosql.lab3.dto;

import ru.nosql.lab3.domain.RequestStatus;
import ru.nosql.lab3.domain.RequestHistoryEntry;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
public record RequestDto(
        String id,
        Integer userId,
        String roomId,
        String ruleId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String purpose,
        int attendeesCount,
        RequestStatus status,
        Instant createdAt,
        Instant reviewedAt,
        String reviewerComment,
        Instant updatedAt,
        List<RequestHistoryEntry> history,
        List<String> tags
) {
}
