package ru.nosql.lab2.dto;

import ru.nosql.lab2.domain.Building;

public record CreateRoomDto(
        Building building,
        int number,
        int capacity
) {
}
