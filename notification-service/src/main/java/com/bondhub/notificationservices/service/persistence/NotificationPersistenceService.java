package com.bondhub.notificationservices.service.persistence;

import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;

import com.bondhub.common.enums.NotificationType;

public interface NotificationPersistenceService {
    Notification persist(BatchedNotificationEvent event);
    Notification buildTransient(BatchedNotificationEvent event);
    
    void updateUnreadState(String userId, String actorId, NotificationType type, String referenceId);
}
