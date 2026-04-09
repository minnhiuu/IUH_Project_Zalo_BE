package com.bondhub.aiservice.client.messageservice;

/** DTO tối giản cho một tin nhắn trong phòng chat */
public record MessageSummary(
        String messageId,
        String senderId,
        String content,
        String sentAt
) {}
