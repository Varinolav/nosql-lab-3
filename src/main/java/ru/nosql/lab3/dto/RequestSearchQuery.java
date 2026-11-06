package ru.nosql.lab3.dto;

import ru.nosql.lab3.domain.RequestAction;
import ru.nosql.lab3.domain.RequestStatus;

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
