package com.bondhub.common.dto;

import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.model.kafka.OutboxEvent;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record OutboxResponse(
        String id,
        String aggregateId,
        String aggregateType,
        EventType eventType,
        OutboxEvent.OutboxEventStatus status,
        Integer retryCount,
        String errorMessage,
        String payload,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
) {}
