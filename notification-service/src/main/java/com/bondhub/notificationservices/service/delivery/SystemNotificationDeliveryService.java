package com.bondhub.notificationservices.service.delivery;

import com.bondhub.common.event.notification.SystemNotificationEvent;

public interface SystemNotificationDeliveryService {

    void deliver(SystemNotificationEvent event);
}
