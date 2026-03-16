package com.bondhub.notificationservices.service.delivery;

import com.bondhub.notificationservices.event.BatchedNotificationEvent;

public interface DeliveryService {

    void deliver(BatchedNotificationEvent event);
}
