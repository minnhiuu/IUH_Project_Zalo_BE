package com.bondhub.socialfeedservice.dto.request.report;

import com.bondhub.socialfeedservice.model.enums.AdminAction;
import com.bondhub.socialfeedservice.model.enums.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BulkModerationRequest(
        @NotBlank(message = "moderation.targetId.required")
        String targetId,

        @NotNull(message = "moderation.targetType.required")
        TargetType targetType,

        @NotNull(message = "moderation.action.required")
        AdminAction action,

        String adminNote
) {
}
