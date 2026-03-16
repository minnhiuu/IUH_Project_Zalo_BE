package com.bondhub.notificationservices.batch;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.RawNotificationEvent;

public interface BatcherService {

    boolean buffer(RawNotificationEvent event);

    boolean isBatchableType(NotificationType type);
}
