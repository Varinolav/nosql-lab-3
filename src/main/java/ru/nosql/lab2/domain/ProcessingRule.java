package ru.nosql.lab2.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("processing_rules")
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingRule {
    @Id
    private String id;

    private String name;
    private String description;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public void revise(String name, String description, Instant updatedAt) {
        this.name = name;
        this.description = description;
        this.updatedAt = updatedAt;
    }
}
