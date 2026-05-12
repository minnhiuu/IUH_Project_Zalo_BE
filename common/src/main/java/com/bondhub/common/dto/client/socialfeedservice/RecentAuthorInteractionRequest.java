package com.bondhub.common.dto.client.socialfeedservice;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RecentAuthorInteractionRequest(
        @NotEmpty List<String> targetUserIds
) {
}
