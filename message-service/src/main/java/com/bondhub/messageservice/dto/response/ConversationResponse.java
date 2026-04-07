package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.enums.Status;
import com.bondhub.common.enums.MessageType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ConversationResponse(
        String id,                        // ObjectId của Conversation
        String recipientId,               // ID của người đang chat cùng (thay cho partnerId)
        String name,                      // Partner name (1-1) hoặc Group name
        String avatar,                    // Partner avatar (1-1) hoặc Group avatar
        Status status,                    // Online/Offline của partner (chỉ 1-1)
        LocalDateTime lastSeenAt,         // Thời điểm online gần nhất của partner
        String friendshipStatus,          // null | PENDING | ACCEPTED | DECLINED | CANCELLED
        boolean isGroup,
        String lastMessage,
        String lastMessageId,
        LocalDateTime lastMessageTime,
        boolean isLastMessageFromMe,
        MessageType lastMessageType,
        Integer unreadCount,
        MessageStatus lastMessageStatus,
        List<ConversationMemberResponse> members) {
}
