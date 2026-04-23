package com.bondhub.socialfeedservice.dto.response.report;

import com.bondhub.socialfeedservice.model.enums.AdminAction;
import com.bondhub.socialfeedservice.model.enums.ReportReason;
import com.bondhub.socialfeedservice.model.enums.ReportStatus;
import com.bondhub.socialfeedservice.model.enums.TargetType;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ReportResponse(
        String id,
        String reporterId,
        String targetId,
        TargetType targetType,
        ReportReason reason,
        String details,
        ReportStatus status,
        String adminId,
        String adminNote,
        AdminAction adminAction,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
