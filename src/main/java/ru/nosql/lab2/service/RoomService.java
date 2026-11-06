package ru.nosql.lab2.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nosql.lab2.domain.Room;
import ru.nosql.lab2.dto.CreateRoomDto;
import ru.nosql.lab2.dto.RoomDto;
import ru.nosql.lab2.exception.NotFoundException;
import ru.nosql.lab2.exception.ValidationException;
import ru.nosql.lab2.domain.RequestStatus;
import ru.nosql.lab2.mapper.RoomMapper;
import ru.nosql.lab2.repository.mongo.MongoBookingRequestRepository;
import ru.nosql.lab2.repository.mongo.MongoRoomRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final MongoRoomRepository roomRepository;
    private final MongoBookingRequestRepository requestRepository;
    private final BookingValidator bookingValidator;
    private final RoomMapper roomMapper;

    @Transactional
    @CacheEvict(cacheNames = "availableRooms", allEntries = true)
    public RoomDto create(CreateRoomDto dto) {
        if (dto == null) {
            throw new ValidationException("Данные аудитории обязательны");
        }
        if (dto.building() == null) {
            throw new ValidationException("Корпус обязателен");
        }
        if (dto.number() <= 0) {
            throw new ValidationException("Номер аудитории должен быть положительным числом");
        }
        if (dto.capacity() <= 0) {
            throw new ValidationException("Вместимость аудитории должна быть больше нуля");
        }

        Room room = new Room(
                null,
                dto.building(),
                dto.number(),
                dto.capacity(),
                true
        );
        return roomMapper.toDto(roomRepository.save(room));
    }

    public RoomDto findById(String id) {
        return roomMapper.toDto(findRoom(id));
    }

    public List<RoomDto> findAllActive() {
        return roomRepository.findAllByActiveTrueOrderByBuildingAscNumberAsc().stream()
                .map(roomMapper::toDto)
                .toList();
    }

    @Cacheable(cacheNames = "availableRooms")
    public List<RoomDto> findAvailable(
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int attendeesCount
    ) {
        bookingValidator.validatePeriod(startsAt, endsAt);
        bookingValidator.validateAttendeesCount(attendeesCount);

        return roomRepository.findAllByActiveTrueOrderByBuildingAscNumberAsc().stream()
                .filter(room -> room.getCapacity() >= attendeesCount)
                .filter(room -> !requestRepository.existsOverlapping(
                        room.getId(),
                        RequestStatus.APPROVED,
                        startsAt,
                        endsAt
                ))
                .map(roomMapper::toDto)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "availableRooms", allEntries = true)
    public void delete(String id) {
        Room room = findRoom(id);
        room.deactivate();
        roomRepository.save(room);
    }

    private Room findRoom(String id) {
        if (id == null) {
            throw new ValidationException("Идентификатор аудитории обязателен");
        }
        return roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Аудитория не найдена: " + id));
    }

}
