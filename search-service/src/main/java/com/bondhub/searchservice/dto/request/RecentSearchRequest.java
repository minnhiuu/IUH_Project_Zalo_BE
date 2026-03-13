package com.bondhub.searchservice.dto.request;

import com.bondhub.searchservice.enums.SearchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecentSearchRequest(
        @NotBlank String id,
        @NotBlank String name,
        String avatar,
        @NotNull SearchType type
) {}
