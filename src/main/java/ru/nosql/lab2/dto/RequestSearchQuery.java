package ru.nosql.lab2.dto;

import ru.nosql.lab2.domain.RequestAction;
import ru.nosql.lab2.domain.RequestStatus;

import java.time.Instant;

public record RequestSearchQuery(
        String roomId,
        String ruleId,
        Integer userId,
        RequestStatus status,
        RequestAction historyAction,
        Instant updatedFrom,
        Instant updatedTo,
        String sortBy,
        String direction,
        int page,
        int size
) {
}
