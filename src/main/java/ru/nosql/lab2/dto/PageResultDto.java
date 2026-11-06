package ru.nosql.lab2.dto;

import java.util.List;

public record PageResultDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        long totalPages
) {
}
