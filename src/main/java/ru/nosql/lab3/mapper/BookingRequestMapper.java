package ru.nosql.lab3.mapper;

import org.mapstruct.Mapper;
import ru.nosql.lab3.domain.BookingRequest;
import ru.nosql.lab3.dto.RequestDto;

@Mapper(componentModel = "spring")
public interface BookingRequestMapper {

    RequestDto toDto(BookingRequest request);
}
