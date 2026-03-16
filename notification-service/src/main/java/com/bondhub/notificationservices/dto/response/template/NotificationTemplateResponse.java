package com.bondhub.notificationservices.dto.response.template;

import com.bondhub.common.enums.NotificationType;

public record NotificationTemplateResponse(
        String id,
        NotificationType type,
        String locale,
        String titleTemplate,
        String bodyTemplate,
        boolean active
) {}
