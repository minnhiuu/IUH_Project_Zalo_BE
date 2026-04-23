package com.bondhub.socialfeedservice.service.report;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.socialfeedservice.dto.request.report.BulkModerationRequest;
import com.bondhub.socialfeedservice.dto.response.report.ContentReportSummary;
import com.bondhub.socialfeedservice.dto.response.report.ReportDetailResponse;
import com.bondhub.socialfeedservice.dto.response.report.ReportResponse;
import com.bondhub.socialfeedservice.dto.response.report.UserWarningResponse;
import com.bondhub.socialfeedservice.model.enums.ReportStatus;
import com.bondhub.socialfeedservice.model.enums.TargetType;

import java.util.List;

public interface ModerationService {

    List<ReportResponse> processReportsForTarget(BulkModerationRequest request);

    PageResponse<List<ContentReportSummary>> getGroupedReports(ReportStatus status, int page, int size);

    List<ReportDetailResponse> getReportsForTarget(String targetId, TargetType targetType);

    List<UserWarningResponse> getUserWarnings(String userId);
}
