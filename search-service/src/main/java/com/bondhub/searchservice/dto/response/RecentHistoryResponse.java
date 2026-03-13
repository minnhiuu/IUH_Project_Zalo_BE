package com.bondhub.searchservice.dto.response;

import lombok.Builder;
import java.util.List;

@Builder
public record RecentHistoryResponse(
    List<RecentSearchResponse> items,
    List<RecentSearchResponse> queries
) {}
