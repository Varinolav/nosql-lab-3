package ru.nosql.lab3.dto;

import ru.nosql.lab3.domain.Building;

public record CreateRoomDto(
        Building building,
        int number,
        int capacity
) {
}
