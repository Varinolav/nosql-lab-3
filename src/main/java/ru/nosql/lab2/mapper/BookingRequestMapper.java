package ru.nosql.lab2.mapper;

import org.mapstruct.Mapper;
import ru.nosql.lab2.domain.BookingRequest;
import ru.nosql.lab2.dto.RequestDto;

@Mapper(componentModel = "spring")
public interface BookingRequestMapper {

    RequestDto toDto(BookingRequest request);
}
