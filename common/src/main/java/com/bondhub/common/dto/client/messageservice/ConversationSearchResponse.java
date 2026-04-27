package com.bondhub.common.dto.client.messageservice;

import lombok.Builder;

@Builder
public record ConversationSearchResponse(
        String conversationId,
        String recipientId,
        String name,
        String avatar,
        boolean group,
        int memberCount,
        String displayHighlights
) {}
