package com.bondhub.messageservice.dto.response;

import com.bondhub.messageservice.model.enums.JoinRequestStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record JoinRequestResponse(
        String id,
        String conversationId,
        String userId,
        String fullName,
        String avatar,
        JoinRequestStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime requestedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime processedAt,
        String processedBy,
        String joinAnswer
) {}
