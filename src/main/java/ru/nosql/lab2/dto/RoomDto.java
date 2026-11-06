package ru.nosql.lab2.dto;

import ru.nosql.lab2.domain.Building;

import java.io.Serializable;

public record RoomDto(
        String id,
        Building building,
        int number,
        int capacity
) implements Serializable {
}
