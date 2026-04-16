package com.bondhub.notificationservices.service.delivery.strategy;

import com.bondhub.notificationservices.model.Notification;

public interface NotificationStrategy {
    void execute(Notification notification);
}
