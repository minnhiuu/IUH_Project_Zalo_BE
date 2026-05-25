package com.bondhub.messageservice.dto.response;

import lombok.Builder;

@Builder
public record UnreadAnchorResponse(
        String firstUnreadMessageId,
        int unreadCount) {
}
