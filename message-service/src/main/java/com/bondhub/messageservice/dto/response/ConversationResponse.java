package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.Status;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ConversationResponse(
                String chatId,
                String partnerId,
                String partnerName,
                String partnerAvatar,
                Status partnerStatus,
                LocalDateTime lastSeenAt,
                String lastMessage,
                LocalDateTime lastMessageTime,
                Integer unreadCount) {
}
