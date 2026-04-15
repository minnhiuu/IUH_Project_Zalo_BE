package com.bondhub.socketservice.dto;

public record TypingPayload(
        String conversationId,
        String userId,
        String userName,
        boolean isTyping,
        String platform  // "PC" | "MOBILE"
) {}
