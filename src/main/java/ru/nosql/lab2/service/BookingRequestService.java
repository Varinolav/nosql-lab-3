package ru.nosql.lab2.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nosql.lab2.domain.BookingRequest;
import ru.nosql.lab2.domain.RequestStatus;
import ru.nosql.lab2.domain.Room;
import ru.nosql.lab2.domain.RequestAction;
import ru.nosql.lab2.domain.RequestHistoryEntry;
import ru.nosql.lab2.dto.CreateRequestDto;
import ru.nosql.lab2.dto.PageResultDto;
import ru.nosql.lab2.dto.RejectRequestDto;
import ru.nosql.lab2.dto.RequestDto;
import ru.nosql.lab2.dto.RequestSearchQuery;
import ru.nosql.lab2.dto.ReviseProcessingRuleDto;
import ru.nosql.lab2.dto.TagCountDto;
import ru.nosql.lab2.exception.ConflictException;
import ru.nosql.lab2.exception.NotFoundException;
import ru.nosql.lab2.exception.ValidationException;
import ru.nosql.lab2.mapper.BookingRequestMapper;
import ru.nosql.lab2.repository.mongo.MongoBookingRequestRepository;
import ru.nosql.lab2.repository.mongo.BookingRequestSearchRepository;
import ru.nosql.lab2.repository.mongo.MongoRoomRepository;
import ru.nosql.lab2.repository.mongo.MongoProcessingRuleRepository;
import ru.nosql.lab2.repository.mongo.BookingLocks;
import ru.nosql.lab2.repository.redis.RedisRequestDraftRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookingRequestService {

    private final MongoBookingRequestRepository requestRepository;
    private final BookingRequestSearchRepository searchRepository;
    private final MongoRoomRepository roomRepository;
    private final MongoProcessingRuleRepository ruleRepository;
    private final ProcessingRuleService processingRuleService;
    private final BookingLocks bookingLocks;
    private final RedisRequestDraftRepository draftRepository;
    private final BookingValidator bookingValidator;
    private final BookingRequestMapper requestMapper;

    @Transactional
    public RequestDto create(CreateRequestDto dto) {
        if (dto == null) {
            throw new ValidationException("Данные заявки обязательны");
        }
        if (dto.userId() == null) {
            throw new ValidationException("Идентификатор пользователя обязателен");
        }
        if (dto.roomId() == null) {
            throw new ValidationException("Идентификатор аудитории обязателен");
        }
        bookingValidator.validatePeriod(dto.startsAt(), dto.endsAt());
        bookingValidator.validateAttendeesCount(dto.attendeesCount());
        bookingValidator.validateRequiredText(dto.purpose(), "Цель бронирования");

        var room = findActiveRoom(dto.roomId());
        if (dto.ruleId() != null) {
            var rule = processingRuleService.requireRule(dto.ruleId());
            if (!rule.isActive()) {
                throw new ConflictException("Правило обработки неактивно");
            }
        }
        validateCapacity(room, dto.attendeesCount());
        ensureRoomIsFree(dto.roomId(), dto.startsAt(), dto.endsAt());

        Instant now = Instant.now();
        BookingRequest request = new BookingRequest(
                null,
                dto.userId(),
                room.getId(),
                dto.ruleId(),
                dto.startsAt(),
                dto.endsAt(),
                dto.purpose(),
                dto.attendeesCount(),
                RequestStatus.PENDING,
                now,
                null,
                null,
                now,
                new ArrayList<>(List.of(new RequestHistoryEntry(RequestAction.CREATED, now, null))),
                dto.tags() == null ? List.of() : dto.tags().stream()
                        .filter(Objects::nonNull)
                        .map(tag -> tag.strip().toLowerCase(Locale.ROOT))
                        .filter(tag -> !tag.isEmpty())
                        .distinct()
                        .toList()
        );

        BookingRequest saved = requestRepository.save(request);
        draftRepository.deleteById(dto.userId());
        return requestMapper.toDto(saved);
    }

    public RequestDto findById(String id) {
        return requestMapper.toDto(findRequest(id));
    }

    public List<TagCountDto> findPopularTags() {
        return requestRepository.findPopularTags();
    }

    public List<RequestDto> findAll() {
        return requestRepository.findAllByOrderByCreatedAtAscIdAsc().stream()
                .map(requestMapper::toDto)
                .toList();
    }

    public List<RequestDto> findAllByStatus(RequestStatus status) {
        if (status == null) {
            throw new ValidationException("Статус заявки обязателен");
        }
        return requestRepository.findAllByStatusOrderByCreatedAtAscIdAsc(status).stream()
                .map(requestMapper::toDto)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "availableRooms", allEntries = true)
    public RequestDto approve(String id) {
        BookingRequest request = findPendingRequestForUpdate(id);
        var room = findActiveRoomForUpdate(request.getRoomId());
        validateCapacity(room, request.getAttendeesCount());

        ensureRoomIsFree(room.getId(), request.getStartsAt(), request.getEndsAt());

        request.approve(Instant.now());
        return requestMapper.toDto(requestRepository.save(request));
    }

    public List<RequestDto> search(String roomId, Instant updatedFrom, Instant updatedTo, RequestStatus status) {
        bookingValidator.validateRequiredText(roomId, "Идентификатор аудитории");
        if (updatedFrom == null || updatedTo == null) {
            throw new ValidationException("Начало и конец периода изменения обязательны");
        }
        if (updatedFrom.isAfter(updatedTo)) {
            throw new ValidationException("Начало периода изменения должно быть не позже окончания");
        }

        List<BookingRequest> requests = status == null
                ? requestRepository.findByRoomAndUpdatedAt(
                        roomId, updatedFrom, updatedTo)
                : requestRepository.findByRoomAndStatusAndUpdatedAt(
                        roomId, status, updatedFrom, updatedTo);
        return requests.stream().map(requestMapper::toDto).toList();
    }

    public PageResultDto<RequestDto> searchPage(RequestSearchQuery filters) {
        if (filters.page() < 0) {
            throw new ValidationException("Номер страницы не может быть отрицательным");
        }
        if (filters.size() < 1 || filters.size() > 100) {
            throw new ValidationException("Размер страницы должен быть от 1 до 100");
        }
        if (filters.userId() != null && filters.userId() < 1) {
            throw new ValidationException("Идентификатор пользователя должен быть положительным");
        }
        if (filters.updatedFrom() != null && filters.updatedTo() != null
                && filters.updatedFrom().isAfter(filters.updatedTo())) {
            throw new ValidationException("Начало периода изменения должно быть не позже окончания");
        }
        if (!Set.of("updatedAt", "createdAt", "startsAt").contains(filters.sortBy())) {
            throw new ValidationException("Недопустимое поле сортировки");
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(filters.direction());
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("Направление сортировки должно быть ASC или DESC");
        }

        Sort sort = Sort.by(direction, filters.sortBy()).and(Sort.by(direction, "_id"));
        long total = searchRepository.count(filters);
        List<RequestDto> content = searchRepository.find(filters, sort).stream()
                .map(requestMapper::toDto)
                .toList();
        long totalPages = (total + filters.size() - 1) / filters.size();
        return new PageResultDto<>(content, filters.page(), filters.size(), total, totalPages);
    }

    @Transactional
    public RequestDto reject(String id, RejectRequestDto dto) {
        if (dto == null) {
            throw new ValidationException("Данные для отклонения заявки обязательны");
        }
        bookingValidator.validateRequiredText(dto.comment(), "Комментарий к отклонению");
        BookingRequest request = findPendingRequestForUpdate(id);

        request.reject(Instant.now(), dto.comment());
        return requestMapper.toDto(requestRepository.save(request));
    }

    @Transactional
    public RequestDto assignRule(String requestId, ru.nosql.lab2.dto.AssignProcessingRuleDto dto) {
        if (dto == null) throw new ValidationException("Выберите правило");
        bookingValidator.validateRequiredText(dto.ruleId(), "Идентификатор правила");
        bookingLocks.lockRule(dto.ruleId());
        var rule = processingRuleService.requireRule(dto.ruleId());
        if (!rule.isActive()) throw new ConflictException("Правило обработки неактивно");
        BookingRequest request = findPendingRequestForUpdate(requestId);
        if (dto.ruleId().equals(request.getRuleId())) return requestMapper.toDto(request);
        request.assignRule(rule.getId(), rule.getName(), Instant.now());
        return requestMapper.toDto(requestRepository.save(request));
    }

    @Transactional
    public RequestDto reviseRule(String requestId, ReviseProcessingRuleDto dto) {
        BookingRequest request = findRequest(requestId);
        if (request.getRuleId() == null) throw new ConflictException("У заявки нет связанного правила обработки");
        reviseSharedRule(request.getRuleId(), dto);
        return requestMapper.toDto(findRequest(requestId));
    }

    @Transactional
    public ru.nosql.lab2.dto.ProcessingRuleDto reviseSharedRule(String ruleId, ReviseProcessingRuleDto dto) {
        if (dto == null || (dto.name() == null && dto.description() == null)) {
            throw new ValidationException("Укажите название или описание правила");
        }
        if (dto.name() != null) bookingValidator.validateRequiredText(dto.name(), "Название правила");
        bookingValidator.validateRequiredText(dto.comment(), "Причина изменения правила");
        bookingLocks.lockRule(ruleId);
        var rule = processingRuleService.requireRule(ruleId);
        Instant now = Instant.now();
        rule.revise(dto.name() == null ? rule.getName() : dto.name().strip(),
                dto.description() == null ? rule.getDescription() : dto.description().strip(), now);
        ruleRepository.save(rule);
        for (var linked : requestRepository.findAllByRuleIdOrderByIdAsc(ruleId)) {
            bookingLocks.lockRequest(linked.getId());
            var request = findRequest(linked.getId());
            request.recordRuleRevision(now, "Правило «" + rule.getName() + "» обновлено. " + dto.comment().strip());
            requestRepository.save(request);
        }
        return processingRuleService.findById(ruleId);
    }
    private BookingRequest findRequest(String id) {
        if (id == null) {
            throw new ValidationException("Идентификатор заявки обязателен");
        }
        return requestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Заявка не найдена: " + id));
    }

    private BookingRequest findPendingRequestForUpdate(String id) {
        if (id == null) {
            throw new ValidationException("Идентификатор заявки обязателен");
        }
        bookingLocks.lockRequest(id);
        BookingRequest request = findRequest(id);
        validatePending(request);
        return request;
    }

    private void validatePending(BookingRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("Можно обработать только заявку, ожидающую рассмотрения");
        }
    }

    private Room findActiveRoom(String roomId) {
        var room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Аудитория не найдена: " + roomId));
        validateActiveRoom(room);
        return room;
    }

    private Room findActiveRoomForUpdate(String roomId) {
        bookingLocks.lockRoom(roomId);
        return findActiveRoom(roomId);
    }

    private void validateActiveRoom(Room room) {
        if (!room.isActive()) {
            throw new ConflictException("Аудитория недоступна для бронирования");
        }
    }

    private void validateCapacity(Room room, int attendeesCount) {
        if (attendeesCount > room.getCapacity()) {
            throw new ConflictException("Вместимость аудитории меньше числа участников");
        }
    }

    private void ensureRoomIsFree(
            String roomId,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        if (requestRepository.existsOverlapping(
                roomId,
                RequestStatus.APPROVED,
                startsAt,
                endsAt
        )) {
            throw new ConflictException("Аудитория уже забронирована на указанный период");
        }
    }

}
