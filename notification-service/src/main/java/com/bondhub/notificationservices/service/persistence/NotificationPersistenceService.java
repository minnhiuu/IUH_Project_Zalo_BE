package com.bondhub.notificationservices.service.persistence;

import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;

public interface NotificationPersistenceService {
    Notification persist(BatchedNotificationEvent event);
}
