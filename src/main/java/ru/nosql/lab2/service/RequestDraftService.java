package ru.nosql.lab2.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.nosql.lab2.domain.RequestDraft;
import ru.nosql.lab2.dto.RequestDraftDto;
import ru.nosql.lab2.dto.SaveRequestDraftDto;
import ru.nosql.lab2.exception.NotFoundException;
import ru.nosql.lab2.exception.ValidationException;
import ru.nosql.lab2.mapper.RequestDraftMapper;
import ru.nosql.lab2.repository.redis.RedisRequestDraftRepository;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RequestDraftService {

    private static final Duration DRAFT_TTL = Duration.ofMinutes(30);

    private final RedisRequestDraftRepository draftRepository;
    private final BookingValidator bookingValidator;
    private final RequestDraftMapper draftMapper;

    public RequestDraftDto save(Integer userId, SaveRequestDraftDto dto) {
        if (userId == null) {
            throw new ValidationException("Идентификатор пользователя обязателен");
        }
        if (dto == null) {
            throw new ValidationException("Данные черновика обязательны");
        }
        if (dto.startsAt() != null && dto.endsAt() != null) {
            bookingValidator.validatePeriod(dto.startsAt(), dto.endsAt());
        }
        if (dto.attendeesCount() != null && dto.attendeesCount() <= 0) {
            throw new ValidationException("Число участников должно быть больше нуля");
        }

        Instant now = Instant.now();
        RequestDraft draft = new RequestDraft(
                userId,
                dto.roomId(),
                dto.startsAt(),
                dto.endsAt(),
                dto.purpose(),
                dto.attendeesCount(),
                now,
                now.plus(DRAFT_TTL),
                dto.tag()
        );
        return draftMapper.toDto(draftRepository.save(draft));
    }

    public RequestDraftDto findByUserId(Integer userId) {
        if (userId == null) {
            throw new ValidationException("Идентификатор пользователя обязателен");
        }
        RequestDraft draft = draftRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Черновик заявки пользователя не найден: " + userId));
        return draftMapper.toDto(draft);
    }

    public void deleteByUserId(Integer userId) {
        if (userId == null) {
            throw new ValidationException("Идентификатор пользователя обязателен");
        }
        draftRepository.deleteById(userId);
    }

}
