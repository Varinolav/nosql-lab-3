package ru.nosql.lab2.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Document("rooms")
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Room {

    @Id
    private String id;

    private Building building;

    private int number;

    private int capacity;

    private boolean active;

    public void deactivate() {
        active = false;
    }
}
