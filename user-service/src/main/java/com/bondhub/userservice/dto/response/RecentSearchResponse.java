package com.bondhub.userservice.dto.response;

import com.bondhub.userservice.model.enums.SearchType;
import lombok.Builder;

@Builder
public record RecentSearchResponse(
        String id,
        String name,
        String avatar,
        SearchType type,
        long timestamp
) {}
