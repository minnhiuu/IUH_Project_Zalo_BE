package com.bondhub.messageservice.dto.response;

import lombok.Builder;

@Builder
public record ConversationMemberResponse(
        String userId,
        String fullName,
        String avatar,
        String lastReadMessageId,
        //replace with enum when group chat is implemented
        String role
) {
}
