package com.bondhub.searchservice.dto.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record MessageSearchResponse(
        String messageId,
        String conversationId,
        String senderId,
        String senderName,
        String senderAvatar,
        String content,
        Long size,
        String type,
        String status,
        boolean hasAttachment,
        boolean hasLink,
        Instant createdAt,
        String highlights
) {}
