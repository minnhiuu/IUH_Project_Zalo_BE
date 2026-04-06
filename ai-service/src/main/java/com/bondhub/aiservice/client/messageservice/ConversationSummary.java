package com.bondhub.aiservice.client.messageservice;

import java.util.List;

/** DTO tối giản cho một phòng chat */
public record ConversationSummary(
        String conversationId,
        String name,
        List<String> memberIds,
        String lastMessage,
        String lastMessageAt,
        boolean isGroup
) {}
