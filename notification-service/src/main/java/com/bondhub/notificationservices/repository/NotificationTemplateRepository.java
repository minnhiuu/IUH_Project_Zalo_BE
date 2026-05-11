package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.model.NotificationTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

public interface NotificationTemplateRepository extends MongoRepository<NotificationTemplate, String> {

    @Query("{ 'type': ?0, 'channel': ?1, 'locale': ?2, 'active': true }")
    Optional<NotificationTemplate> findByTypeAndChannelAndLocaleAndActiveTrue(
            NotificationType type,
            NotificationChannel channel,
            String locale
    );

    @Query("{ 'type': { $in: ?0 }, 'channel': ?1, 'locale': ?2, 'active': true }")
    List<NotificationTemplate> findByTypeInAndChannelAndLocaleAndActiveTrue(
            List<NotificationType> types,
            NotificationChannel channel,
            String locale
    );
}