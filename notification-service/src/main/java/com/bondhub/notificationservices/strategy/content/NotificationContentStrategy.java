package com.bondhub.notificationservices.strategy.content;

import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.model.Notification;

public interface NotificationContentStrategy {

    NotificationType getType();

    Notification build(Object request);
}
