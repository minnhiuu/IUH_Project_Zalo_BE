package com.bondhub.notificationservices.service.notification.assembler;

import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.event.RawNotificationEvent;

public interface NotificationAssembler {

    NotificationType getType();

    RawNotificationEvent build(Object request);
}
