package ru.nosql.lab3.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.nosql.lab3.domain.ProcessingRule;
import ru.nosql.lab3.dto.CreateProcessingRuleDto;
import ru.nosql.lab3.dto.ProcessingRuleDto;
import ru.nosql.lab3.exception.NotFoundException;
import ru.nosql.lab3.exception.ValidationException;
import ru.nosql.lab3.repository.mongo.MongoProcessingRuleRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessingRuleService {
    private final MongoProcessingRuleRepository rules;

    public ProcessingRuleDto create(CreateProcessingRuleDto dto) {
        if (dto == null || dto.name() == null || dto.name().isBlank()) {
            throw new ValidationException("Название правила обязательно");
        }
        Instant now = Instant.now();
        ProcessingRule rule = new ProcessingRule(null, dto.name().strip(),
                dto.description() == null ? "" : dto.description().strip(), true, now, now);
        return toDto(rules.save(rule));
    }

    public List<ProcessingRuleDto> findAll() {
        return rules.findAllByOrderByNameAscIdAsc().stream().map(this::toDto).toList();
    }

    public ProcessingRuleDto findById(String id) {
        return toDto(requireRule(id));
    }

    public ProcessingRule requireRule(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Идентификатор правила обязателен");
        }
        return rules.findById(id)
                .orElseThrow(() -> new NotFoundException("Правило обработки не найдено: " + id));
    }

    private ProcessingRuleDto toDto(ProcessingRule rule) {
        return new ProcessingRuleDto(rule.getId(), rule.getName(), rule.getDescription(),
                rule.isActive(), rule.getCreatedAt(), rule.getUpdatedAt());
    }
}
