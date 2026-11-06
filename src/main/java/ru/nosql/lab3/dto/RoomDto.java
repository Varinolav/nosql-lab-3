package ru.nosql.lab3.dto;

import ru.nosql.lab3.domain.Building;

import java.io.Serializable;

public record RoomDto(
        String id,
        Building building,
        int number,
        int capacity
) implements Serializable {
}
