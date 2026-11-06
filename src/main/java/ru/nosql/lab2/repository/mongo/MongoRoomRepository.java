package ru.nosql.lab2.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.nosql.lab2.domain.Room;

import java.util.List;

public interface MongoRoomRepository extends MongoRepository<Room, String> {
    List<Room> findAllByActiveTrueOrderByBuildingAscNumberAsc();
}
