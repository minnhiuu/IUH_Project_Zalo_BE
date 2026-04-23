package com.bondhub.searchservice.dto.response;

import lombok.Builder;
import java.time.Instant;
import java.util.List;

@Builder
public record MessageSyncResponse(
        String id,
        String conversationId,
        String senderId,
        String senderName,
        String senderAvatar,
        String content,
        String type,
        String status,
        boolean hasAttachment,
        boolean hasLink,
        String linkGroupName,
        String linkUrl,
        String originalFileName,
        Long size,
        Instant createdAt,
        List<String> deletedBy,
        List<String> visibleTo
) {}
