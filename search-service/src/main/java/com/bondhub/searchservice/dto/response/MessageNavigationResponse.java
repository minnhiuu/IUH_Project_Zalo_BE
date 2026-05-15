package com.bondhub.searchservice.dto.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record MessageNavigationResponse(
        String messageId,
        String conversationId,
        int index,
        int total,
        Instant createdAt,
        String displayHighlights,
        String direction
) {}
