package com.bondhub.userservice.dto.request.recentsearch;

import com.bondhub.userservice.model.enums.SearchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecentSearchRequest(
        @NotBlank String id,
        @NotBlank String name,
        String avatar,
        @NotNull SearchType type
) {}
