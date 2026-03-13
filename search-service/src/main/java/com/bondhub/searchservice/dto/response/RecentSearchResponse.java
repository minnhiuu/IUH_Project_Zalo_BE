package com.bondhub.searchservice.dto.response;

import com.bondhub.searchservice.enums.SearchType;
import lombok.Builder;

@Builder
public record RecentSearchResponse(
        String id,
        String name,
        String avatar,
        SearchType type,
        long timestamp
) {}
