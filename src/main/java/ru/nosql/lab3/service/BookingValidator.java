package ru.nosql.lab3.service;

import org.springframework.stereotype.Component;
import ru.nosql.lab3.exception.ValidationException;

import java.time.LocalDateTime;

@Component
public class BookingValidator {

    public void validatePeriod(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null) {
            throw new ValidationException("Время начала и окончания бронирования обязательны");
        }
        if (!startsAt.isBefore(endsAt)) {
            throw new ValidationException("Время начала бронирования должно быть раньше времени окончания");
        }
    }

    public void validateAttendeesCount(int attendeesCount) {
        if (attendeesCount <= 0) {
            throw new ValidationException("Число участников должно быть больше нуля");
        }
    }

    public void validateRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Поле «" + fieldName + "» обязательно");
        }
    }
}
