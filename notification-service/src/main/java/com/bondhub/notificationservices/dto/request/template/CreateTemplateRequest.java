package com.bondhub.notificationservices.dto.request.template;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.common.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTemplateRequest(

        @NotNull(message = "validation.notification.type.required")
        NotificationType type,

        @NotNull(message = "validation.notification.channel.required")
        NotificationChannel channel,

        @NotBlank(message = "validation.notification.locale.required")
        String locale,

        @NotBlank(message = "validation.notification.title.required")
        String titleTemplate,

        @NotBlank(message = "validation.notification.body.required")
        String bodyTemplate
) {}
