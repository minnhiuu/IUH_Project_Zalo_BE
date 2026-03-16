package com.bondhub.searchservice.dto.response;

import com.bondhub.common.model.kafka.EventType;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record FailedEventResponse(
    String id,
    String eventId,
    EventType eventType,
    String topic,
    String payload,
    String errorMessage,
    String stackTrace,
    Integer partition,
    Long offset,
    LocalDateTime createdAt,
    int retryCount,
    boolean resolved
) {}
