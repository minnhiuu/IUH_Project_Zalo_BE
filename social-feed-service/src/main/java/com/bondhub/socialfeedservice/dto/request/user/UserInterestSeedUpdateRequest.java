package com.bondhub.socialfeedservice.dto.request.user;

import java.util.Set;

public record UserInterestSeedUpdateRequest(
        Set<String> initialInterests
) {
}