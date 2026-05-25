package com.bondhub.searchservice.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record MessageSearchGroupResponse(
        String messageId,
        String conversationId,
        String title,
        String avatar,
        boolean isGroup,
        long matchCount,
        String previewContent,
        String previewHighlights,
        String previewType,
        Long size,
        boolean hasAttachment,
        boolean hasLink,
        Instant lastMatchedAt,
        List<String> participantNames,
        List<String> participantAvatars
) {}
