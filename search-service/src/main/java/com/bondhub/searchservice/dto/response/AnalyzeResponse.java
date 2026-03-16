package com.bondhub.searchservice.dto.response;

import lombok.Builder;
import java.util.List;

@Builder
public record AnalyzeResponse(
        String analyzer,
        List<TokenDetail> tokens
) {
    public record TokenDetail(
            String token,
            int startOffset,
            int endOffset,
            String type,
            int position
    ) {}
}
