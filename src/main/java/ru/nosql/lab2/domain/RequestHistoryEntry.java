package ru.nosql.lab2.domain;

import java.time.Instant;

public record RequestHistoryEntry(
        RequestAction action,
        Instant occurredAt,
        String comment
) {
}
