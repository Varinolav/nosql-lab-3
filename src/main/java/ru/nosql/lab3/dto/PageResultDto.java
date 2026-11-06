package ru.nosql.lab3.dto;

import java.util.List;

public record PageResultDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        long totalPages
) {
}
