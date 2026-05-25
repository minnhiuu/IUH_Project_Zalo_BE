package com.bondhub.notificationservices.service.delivery.renderer;

import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.enums.NotificationChannel;
import java.util.Map;

public interface NotificationRenderer {
    String renderBody(Notification notification, NotificationChannel channel, String locale, Map<String, Object> renderData);
}
