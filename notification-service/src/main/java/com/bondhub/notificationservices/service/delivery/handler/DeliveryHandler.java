package com.bondhub.notificationservices.service.delivery.handler;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;

public interface DeliveryHandler {

    NotificationChannel getChannel();

    void deliver(BatchedNotificationEvent event);
}
