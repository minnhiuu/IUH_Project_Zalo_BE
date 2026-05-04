package com.bondhub.searchservice.dto.response;

import lombok.Builder;
import java.time.Instant;
import java.util.List;

@Builder
public record MessageSearchResponse(
        String messageId,
        String conversationId,
        String senderId,
        String senderName,
        String senderAvatar,
        String displayContent,
        Long size,
        String type,
        String status,
        boolean hasAttachment,
        boolean hasLink,
        boolean isGroup,
        String conversationName,
        String conversationAvatar,
        List<String> participantNames,
        List<String> participantAvatars,
        Instant createdAt,
        String displayHighlights
) {}
