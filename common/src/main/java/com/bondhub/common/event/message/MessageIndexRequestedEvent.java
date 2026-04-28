package com.bondhub.common.event.message;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record MessageIndexRequestedEvent(
        String messageId,
        String conversationId,
        List<String> participantIds,
        List<String> participantNames,
        List<String> participantAvatars,
        String conversationName,
        String conversationAvatar,
        boolean group,
        String senderId,
        String senderName,
        String senderAvatar,
        String content,
        String linkGroupName,
        String linkUrl,
        String originalFileName,
        String fileExtension,
        Long size,
        String searchableText,
        String conversationSearchText,
        String type,
        String status,
        boolean hasAttachment,
        boolean hasLink,
        Instant createdAt,
        List<String> deletedBy,
        List<String> visibleTo
) {}
