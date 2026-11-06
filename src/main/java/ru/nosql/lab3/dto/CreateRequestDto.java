package ru.nosql.lab3.dto;

import java.time.LocalDateTime;
import java.util.List;
public record CreateRequestDto(
        Integer userId,
        String roomId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String purpose,
        int attendeesCount,
        List<String> tags,
        String ruleId
) {
    public CreateRequestDto(Integer userId, String roomId, LocalDateTime startsAt,
                            LocalDateTime endsAt, String purpose, int attendeesCount,
                            List<String> tags) {
        this(userId, roomId, startsAt, endsAt, purpose, attendeesCount, tags, null);
    }
}
