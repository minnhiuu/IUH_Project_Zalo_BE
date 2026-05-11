package com.bondhub.common.dto.client.messageservice;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RecentChatInteractionRequest(
        @NotEmpty List<String> targetUserIds
) {
}
