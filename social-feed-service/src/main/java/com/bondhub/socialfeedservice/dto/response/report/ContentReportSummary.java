package com.bondhub.socialfeedservice.dto.response.report;

import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import com.bondhub.socialfeedservice.model.enums.ReportReason;
import com.bondhub.socialfeedservice.model.enums.ReportStatus;
import com.bondhub.socialfeedservice.model.enums.TargetType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ContentReportSummary(
        String targetId,
        TargetType targetType,
        int totalReports,
        int pendingCount,
        int resolvedCount,
        int dismissedCount,
        List<ReportReason> reasons,
        LocalDateTime latestReportAt,
        String contentText,
        List<String> contentMediaUrls,
        String contentAuthorId,
        AuthorInfo contentAuthorInfo,
        ReportStatus overallStatus
) {
}
