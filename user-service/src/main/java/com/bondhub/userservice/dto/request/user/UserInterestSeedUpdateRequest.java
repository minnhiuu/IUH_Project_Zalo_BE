package com.bondhub.userservice.dto.request.user;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UserInterestSeedUpdateRequest(
        @NotEmpty Set<String> initialInterests
) {
}