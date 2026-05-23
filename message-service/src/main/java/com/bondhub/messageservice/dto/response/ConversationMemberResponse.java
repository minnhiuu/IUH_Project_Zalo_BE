package com.bondhub.messageservice.dto.response;

import com.bondhub.messageservice.model.enums.MemberRole;
import lombok.Builder;

@Builder
public record ConversationMemberResponse(
        String userId,
        String fullName,
        String avatar,
        String lastReadMessageId,
        MemberRole role,
        Boolean pinned,
        Boolean muted,
        Boolean hidden,
        Boolean manuallyMarkedUnread
) {
}
