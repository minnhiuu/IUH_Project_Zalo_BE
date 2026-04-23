package com.bondhub.socialfeedservice.dto.request.report;

import com.bondhub.socialfeedservice.model.enums.ReportReason;
import com.bondhub.socialfeedservice.model.enums.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReportRequest(
        @NotBlank(message = "report.targetId.required")
        String targetId,

        @NotNull(message = "report.targetType.required")
        TargetType targetType,

        @NotNull(message = "report.reason.required")
        ReportReason reason,

        String details
) {
}
