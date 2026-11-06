package ru.nosql.lab3.mapper;

import org.mapstruct.Mapper;
import ru.nosql.lab3.domain.Room;
import ru.nosql.lab3.dto.RoomDto;

@Mapper(componentModel = "spring")
public interface RoomMapper {

    RoomDto toDto(Room room);
}
