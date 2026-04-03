package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.enums.Status;
import com.bondhub.common.enums.MessageType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ConversationResponse(
        String conversationId,
        String partnerId,
        String partnerName, //replace to conversation name when implementing group chat functionality
        String partnerAvatar,
        Status partnerStatus,
        LocalDateTime lastSeenAt,
        String lastMessage,
        String lastMessageId,
        LocalDateTime lastMessageTime,
        boolean isLastMessageFromMe,
        MessageType lastMessageType,
        Integer unreadCount,
        MessageStatus lastMessageStatus,
        List<ConversationMemberResponse> members) {
}
