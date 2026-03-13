package com.bondhub.searchservice.dto.request;

import lombok.Builder;

@Builder
public record AnalyzeRequest(
    String index,
    String analyzer,
    String text
) {}
