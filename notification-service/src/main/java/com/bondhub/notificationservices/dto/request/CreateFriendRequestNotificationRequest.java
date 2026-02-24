package com.bondhub.notificationservices.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateFriendRequestNotificationRequest(

        @NotBlank(message = "ReceiverId must not be blank")
        String receiverId,

        @NotBlank(message = "RequestId must not be blank")
        String requestId,

        @NotBlank(message = "SenderId must not be blank")
        String senderId,

        @NotBlank(message = "SenderName must not be blank")
        String senderName
) {}
