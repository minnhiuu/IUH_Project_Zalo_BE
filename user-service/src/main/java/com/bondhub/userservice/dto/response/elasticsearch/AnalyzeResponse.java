package com.bondhub.userservice.dto.response.elasticsearch;

import lombok.Builder;
import java.util.List;

@Builder
public record AnalyzeResponse(
    List<AnalyzeToken> tokens
) {
    @Builder
    public record AnalyzeToken(
        String token,
        int startOffset,
        int endOffset,
        String type,
        int position
    ) {}
}
