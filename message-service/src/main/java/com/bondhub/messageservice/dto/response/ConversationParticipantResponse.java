package com.bondhub.messageservice.dto.response;

import lombok.Builder;

@Builder
public record ConversationParticipantResponse(
        String userId,
        String fullName,
        String avatar,
        boolean isMe
) {}
