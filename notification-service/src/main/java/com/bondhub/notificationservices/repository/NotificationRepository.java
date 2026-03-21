package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {


    int countByUserId(String targetUserId);
}
