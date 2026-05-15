package com.bondhub.common.event.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatInteractionOccurredEvent(
        String userId,
        String targetUserId,
        String conversationId,
        Instant occurredAt
) {
}
