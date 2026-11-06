package ru.nosql.lab3.mapper;

import org.mapstruct.Mapper;
import ru.nosql.lab3.domain.RequestDraft;
import ru.nosql.lab3.dto.RequestDraftDto;

@Mapper(componentModel = "spring")
public interface RequestDraftMapper {

    RequestDraftDto toDto(RequestDraft draft);
}
