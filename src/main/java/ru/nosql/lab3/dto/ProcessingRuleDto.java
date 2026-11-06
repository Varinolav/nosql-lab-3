package ru.nosql.lab3.dto;

import java.time.Instant;

public record ProcessingRuleDto(
        String id,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
