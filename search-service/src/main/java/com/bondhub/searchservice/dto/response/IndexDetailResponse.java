package com.bondhub.searchservice.dto.response;

import java.time.LocalDateTime;
import com.bondhub.searchservice.enums.IndexStatus;
import lombok.Builder;

@Builder
public record IndexDetailResponse(
        String indexName,
        LocalDateTime createdAt,
        long docCount,
        String primaryStoreSize,
        IndexStatus status
) {}
