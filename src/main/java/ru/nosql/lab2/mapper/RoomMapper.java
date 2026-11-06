package ru.nosql.lab2.mapper;

import org.mapstruct.Mapper;
import ru.nosql.lab2.domain.Room;
import ru.nosql.lab2.dto.RoomDto;

@Mapper(componentModel = "spring")
public interface RoomMapper {

    RoomDto toDto(Room room);
}
