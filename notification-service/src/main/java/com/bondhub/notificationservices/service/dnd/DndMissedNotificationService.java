package com.bondhub.notificationservices.service.dnd;

import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;

public interface DndMissedNotificationService {

    void record(Notification notification);

    void record(BatchedNotificationEvent event);
}
