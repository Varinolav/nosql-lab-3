package ru.nosql.lab2.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.nosql.lab2.domain.RequestStatus;
import ru.nosql.lab2.domain.RequestAction;
import ru.nosql.lab2.dto.*;
import ru.nosql.lab2.service.BookingRequestService;

import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final BookingRequestService bookingRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto create(@RequestBody CreateRequestDto dto) {
        return bookingRequestService.create(dto);
    }

    @GetMapping("/tags/popular")
    public List<TagCountDto> findPopularTags() {
        return bookingRequestService.findPopularTags();
    }

    @GetMapping("/{id}")
    public RequestDto findById(@PathVariable String id) {
        return bookingRequestService.findById(id);
    }

    @GetMapping("/search")
    public PageResultDto<RequestDto> search(
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) RequestAction historyAction,
            @RequestParam(required = false) Instant updatedFrom,
            @RequestParam(required = false) Instant updatedTo,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return bookingRequestService.searchPage(new RequestSearchQuery(roomId, ruleId, userId, status,
                historyAction, updatedFrom, updatedTo, sortBy, direction, page, size));
    }

    @GetMapping
    public List<RequestDto> findAll(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) Instant updatedFrom,
            @RequestParam(required = false) Instant updatedTo
    ) {
        if (roomId != null || updatedFrom != null || updatedTo != null) {
            return bookingRequestService.search(roomId, updatedFrom, updatedTo, status);
        }
        return status == null
                ? bookingRequestService.findAll()
                : bookingRequestService.findAllByStatus(status);
    }

    @PatchMapping("/{id}/approve")
    public RequestDto approve(@PathVariable String id) {
        return bookingRequestService.approve(id);
    }

    @PatchMapping("/{id}/reject")
    public RequestDto reject(@PathVariable String id, @RequestBody RejectRequestDto dto) {
        return bookingRequestService.reject(id, dto);
    }

    @PatchMapping("/{id}/rule")
    public RequestDto reviseRule(@PathVariable String id, @RequestBody ReviseProcessingRuleDto dto) {
        return bookingRequestService.reviseRule(id, dto);
    }

    @PatchMapping("/{id}/rule-assignment")
    public RequestDto assignRule(@PathVariable String id,
            @RequestBody AssignProcessingRuleDto dto) {
        return bookingRequestService.assignRule(id, dto);
    }
}
