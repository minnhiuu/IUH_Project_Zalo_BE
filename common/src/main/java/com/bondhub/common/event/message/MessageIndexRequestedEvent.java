package com.bondhub.common.event.message;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record MessageIndexRequestedEvent(
        String messageId,
        String conversationId,
        String senderId,
        String senderName,
        String senderAvatar,
        String content,
        String linkGroupName,
        String linkUrl,
        String originalFileName,
        Long size,
        String searchableText,
        String type,
        String status,
        boolean hasAttachment,
        boolean hasLink,
        Instant createdAt,
        List<String> deletedBy,
        List<String> visibleTo
) {}
