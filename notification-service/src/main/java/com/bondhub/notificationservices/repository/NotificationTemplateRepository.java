package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.model.NotificationTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends MongoRepository<NotificationTemplate, String> {

    Optional<NotificationTemplate> findByTypeAndChannelAndLocaleAndActiveTrue(
            NotificationType type,
            NotificationChannel channel,
            String locale
    );

    List<NotificationTemplate> findByTypeInAndChannelAndLocaleAndActiveTrue(
            List<NotificationType> types,
            NotificationChannel channel,
            String locale
    );
}