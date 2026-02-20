package com.bondhub.notificationservices.dto.request.notification;

import jakarta.validation.constraints.NotBlank;

public record CreateFriendRequestNotificationRequest(
        @NotBlank(message = "validation.receiver.id.required")
        String receiverId,

        @NotBlank(message = "validation.sender.id.required")
        String senderId,

        @NotBlank(message = "validation.sender.name.required")
        String senderName,

        @NotBlank(message = "validation.request.id.required")
        String requestId,

        @NotBlank(message = "validation.notification.locale.required")
        String locale
) {}
