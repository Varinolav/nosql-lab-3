package ru.nosql.lab3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.nosql.lab3.dto.CreateProcessingRuleDto;
import ru.nosql.lab3.dto.ProcessingRuleDto;
import ru.nosql.lab3.dto.ReviseProcessingRuleDto;
import ru.nosql.lab3.service.ProcessingRuleService;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class ProcessingRuleController {
    private final ProcessingRuleService rules;
    private final ru.nosql.lab3.service.BookingRequestService requests;

    @PatchMapping("/{id}")
    public ProcessingRuleDto revise(@PathVariable String id,
            @RequestBody ReviseProcessingRuleDto dto) {
        return requests.reviseSharedRule(id, dto);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessingRuleDto create(@RequestBody CreateProcessingRuleDto dto) {
        return rules.create(dto);
    }

    @GetMapping
    public List<ProcessingRuleDto> findAll() {
        return rules.findAll();
    }

    @GetMapping("/{id}")
    public ProcessingRuleDto findById(@PathVariable String id) {
        return rules.findById(id);
    }
}
