package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.enums.DndMissedStatus;
import com.bondhub.notificationservices.model.DndMissedNotification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DndMissedNotificationRepository extends MongoRepository<DndMissedNotification, String> {

    List<DndMissedNotification> findByUserIdAndStatus(
            String userId,
            DndMissedStatus status
    );
}
