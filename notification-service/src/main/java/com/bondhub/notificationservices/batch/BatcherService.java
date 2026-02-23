package com.bondhub.notificationservices.batch;

import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.event.RawNotificationEvent;

public interface BatcherService {

    boolean buffer(RawNotificationEvent event);

    boolean isBatchableType(NotificationType type);
}
