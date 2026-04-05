package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.Status;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record ConversationResponse(
        String id,                        // ObjectId của Conversation
        String name,                      // Partner name (1-1) hoặc Group name
        String avatar,                    // Partner avatar (1-1) hoặc Group avatar
        Status status,                    // Online/Offline của partner (chỉ 1-1)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "GMT+7") OffsetDateTime lastSeenAt,         // Thời điểm online gần nhất của partner
        boolean isGroup,
        Integer unreadCount,
        LastMessageResponse lastMessage,
        List<ConversationMemberResponse> members) {
}
