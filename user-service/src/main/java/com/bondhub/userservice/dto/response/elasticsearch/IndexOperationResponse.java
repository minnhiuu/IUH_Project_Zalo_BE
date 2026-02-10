package com.bondhub.userservice.dto.response.elasticsearch;

import lombok.Builder;

@Builder
public record IndexOperationResponse(
    String message,
    String indexName
) {}
