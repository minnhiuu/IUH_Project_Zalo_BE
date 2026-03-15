package com.bondhub.messageservice.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Chat Notification DTO
 * Uses Java Record as per backend development rules
 */
@Builder
public record ChatNotification(
    String id,
    String chatId,
    String senderId,
    String senderName,
    String senderAvatar,
    String recipientId,
    String content,
    String clientMessageId,
    LocalDateTime timestamp,
    Integer unreadCount
) {}
