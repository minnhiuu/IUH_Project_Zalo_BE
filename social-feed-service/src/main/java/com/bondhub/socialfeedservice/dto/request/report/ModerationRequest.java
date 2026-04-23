package com.bondhub.socialfeedservice.dto.request.report;

import com.bondhub.socialfeedservice.model.enums.AdminAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ModerationRequest(
        @NotBlank(message = "moderation.reportId.required")
        String reportId,

        @NotNull(message = "moderation.action.required")
        AdminAction action,

        String adminNote
) {
}
