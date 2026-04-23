package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.enums.MessageType;
import lombok.Builder;

import java.time.Instant;
import java.util.Set;

@Builder
public record MessageSyncResponse(
        String id,
        String conversationId,
        String senderId,
        String senderName,
        String senderAvatar,
        String content,
        MessageType type,
        MessageStatus status,
        boolean hasAttachment,
        boolean hasLink,
        String linkGroupName,
        String linkUrl,
        String originalFileName,
        Long size,
        Instant createdAt,
        Set<String> deletedBy,
        Set<String> visibleTo
) {}
