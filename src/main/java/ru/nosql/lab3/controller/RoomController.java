package ru.nosql.lab3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.nosql.lab3.dto.CreateRoomDto;
import ru.nosql.lab3.dto.RoomDto;
import ru.nosql.lab3.service.RoomService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomDto create(@RequestBody CreateRoomDto dto) {
        return roomService.create(dto);
    }

    @GetMapping("/{id}")
    public RoomDto findById(@PathVariable String id) {
        return roomService.findById(id);
    }

    @GetMapping
    public List<RoomDto> findAllActive() {
        return roomService.findAllActive();
    }

    @GetMapping("/available")
    public List<RoomDto> findAvailable(
            @RequestParam LocalDateTime startsAt,
            @RequestParam LocalDateTime endsAt,
            @RequestParam int attendeesCount
    ) {
        return roomService.findAvailable(startsAt, endsAt, attendeesCount);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        roomService.delete(id);
    }
}
