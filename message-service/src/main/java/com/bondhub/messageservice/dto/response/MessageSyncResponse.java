package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.enums.MessageType;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Builder
public record MessageSyncResponse(
        String id,
        String conversationId,
        List<String> participantIds,
        List<String> participantNames,
        List<String> participantAvatars,
        String conversationName,
        String conversationAvatar,
        boolean group,
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
        String searchableText,
        String conversationSearchText,
        Instant createdAt,
        Set<String> deletedBy,
        Set<String> visibleTo
) {}
