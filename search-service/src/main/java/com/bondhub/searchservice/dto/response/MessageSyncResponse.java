package com.bondhub.searchservice.dto.response;

import lombok.Builder;
import java.time.Instant;
import java.util.List;

@Builder
public record MessageSyncResponse(
        String id,
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
        String type,
        String status,
        boolean hasAttachment,
        boolean hasLink,
        String linkGroupName,
        String linkUrl,
        String originalFileName,
        Long size,
        String searchableText,
        String conversationSearchText,
        Instant createdAt,
        List<String> deletedBy,
        List<String> visibleTo
) {}
