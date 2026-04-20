package com.bondhub.socialfeedservice.dto.response.report;

import com.bondhub.socialfeedservice.model.enums.ReportReason;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record UserWarningResponse(
        String id,
        String userId,
        String reportId,
        ReportReason reason,
        String adminId,
        String adminNote,
        LocalDateTime createdAt
) {
}
