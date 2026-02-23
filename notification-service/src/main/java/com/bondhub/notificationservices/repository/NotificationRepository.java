package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    long countByUserIdAndTypeAndCreatedAtAfter(String userId, NotificationType type, LocalDateTime oneMinuteAgo);
}
