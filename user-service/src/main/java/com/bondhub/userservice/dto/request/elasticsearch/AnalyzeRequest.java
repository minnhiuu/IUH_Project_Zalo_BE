package com.bondhub.userservice.dto.request.elasticsearch;

import lombok.Builder;

@Builder
public record AnalyzeRequest(
    String index,
    String analyzer,
    String text
) {}
