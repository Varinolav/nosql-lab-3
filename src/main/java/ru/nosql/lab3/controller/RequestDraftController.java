package ru.nosql.lab3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.nosql.lab3.dto.RequestDraftDto;
import ru.nosql.lab3.dto.SaveRequestDraftDto;
import ru.nosql.lab3.service.RequestDraftService;


@RestController
@RequestMapping("/api/users/{userId}/request-draft")
@RequiredArgsConstructor
public class RequestDraftController {

    private final RequestDraftService requestDraftService;

    @PutMapping
    public RequestDraftDto save(@PathVariable Integer userId, @RequestBody SaveRequestDraftDto dto) {
        return requestDraftService.save(userId, dto);
    }

    @GetMapping
    public RequestDraftDto findByUserId(@PathVariable Integer userId) {
        return requestDraftService.findByUserId(userId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer userId) {
        requestDraftService.deleteByUserId(userId);
    }
}
