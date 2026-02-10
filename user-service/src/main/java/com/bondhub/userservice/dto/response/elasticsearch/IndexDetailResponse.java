package com.bondhub.userservice.dto.response.elasticsearch;

import com.bondhub.userservice.enums.IndexStatus;
import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record IndexDetailResponse(
    String indexName,
    LocalDateTime createdAt,
    long docCount,
    String primaryStoreSize,
    IndexStatus status
) {}
