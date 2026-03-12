package com.bondhub.notificationservices.service.notification.assembler;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.RawNotificationEvent;

public interface NotificationAssembler {

    NotificationType getType();

    RawNotificationEvent build(Object request);
}
