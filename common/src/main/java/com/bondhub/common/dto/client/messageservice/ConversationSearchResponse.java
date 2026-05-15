package com.bondhub.common.dto.client.messageservice;

import java.util.List;

import lombok.Builder;

@Builder
public record ConversationSearchResponse(
        String conversationId,
        String recipientId,
        String name,
        String avatar,
        boolean group,
        int memberCount,
        List<String> participantNames,
        List<String> participantAvatars,
        String displayHighlights,
        String phoneNumber
) {}
