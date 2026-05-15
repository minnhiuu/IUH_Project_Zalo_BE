package com.bondhub.searchservice.dto.request;

import com.bondhub.searchservice.enums.SearchEventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SearchEventRequest(
        @NotBlank String keyword,
        @NotBlank String targetUserId,
        @Min(0) Integer rank,
        @NotNull SearchEventType eventType
) {
}
