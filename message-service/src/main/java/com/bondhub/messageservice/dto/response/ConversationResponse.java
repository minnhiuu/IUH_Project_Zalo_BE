package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.Status;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ConversationResponse(
        String chatId,
        String partnerId,
        String partnerName,
        String partnerAvatar,
        Status partnerStatus,
        LocalDateTime lastSeenAt,
        String lastMessage,
        String lastMessageId,
        LocalDateTime lastMessageTime,
        Integer unreadCount,
        List<ConversationMemberResponse> members) {
}
