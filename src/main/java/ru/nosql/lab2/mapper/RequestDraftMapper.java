package ru.nosql.lab2.mapper;

import org.mapstruct.Mapper;
import ru.nosql.lab2.domain.RequestDraft;
import ru.nosql.lab2.dto.RequestDraftDto;

@Mapper(componentModel = "spring")
public interface RequestDraftMapper {

    RequestDraftDto toDto(RequestDraft draft);
}
