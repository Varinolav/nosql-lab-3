package ru.nosql.lab3.controller;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.nosql.lab3.exception.ConflictException;
import ru.nosql.lab3.exception.NotFoundException;
import ru.nosql.lab3.exception.ValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({DataIntegrityViolationException.class, TransientDataAccessException.class})
    public ResponseEntity<ApiErrorDto> handleStorageConflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorDto(409,
                "Конфликт изменения данных. Обновите данные и повторите запрос."));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorDto> handleValidation(ValidationException exception) {
        return error(HttpStatus.BAD_REQUEST, exception);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorDto> handleNotFound(NotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorDto> handleConflict(ConflictException exception) {
        return error(HttpStatus.CONFLICT, exception);
    }

    private ResponseEntity<ApiErrorDto> error(HttpStatus status, RuntimeException exception) {
        return ResponseEntity.status(status)
                .body(new ApiErrorDto(status.value(), exception.getMessage()));
    }
}
