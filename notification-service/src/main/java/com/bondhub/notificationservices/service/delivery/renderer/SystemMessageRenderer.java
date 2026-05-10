package com.bondhub.notificationservices.service.delivery.renderer;

import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.service.template.NotificationTemplateService;
import com.bondhub.notificationservices.dto.response.template.NotificationTemplateResponse;
import com.bondhub.notificationservices.service.delivery.renderer.internal.SystemActionRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SystemMessageRenderer implements NotificationRenderer {

    private final NotificationTemplateService templateService;

    @Override
    public String renderBody(Notification notification, NotificationChannel channel, String locale, Map<String, Object> renderData) {
        if (channel != NotificationChannel.FCM) {
            return "";
        }

        try {
            NotificationTemplateResponse template = templateService.getTemplate(
                    notification.getType(),
                    channel,
                    locale
            );
            return templateService.render(template.bodyTemplate(), renderData);
        } catch (Exception e) {
            log.debug("Template not found for system action, using hardcoded rendering", e);
            return SystemActionRenderer.render(notification, locale);
        }
    }
}
