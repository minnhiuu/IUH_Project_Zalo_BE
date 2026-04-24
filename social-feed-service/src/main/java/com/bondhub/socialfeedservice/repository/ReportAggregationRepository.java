package com.bondhub.socialfeedservice.repository;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.socialfeedservice.dto.response.report.ContentReportSummary;
import com.bondhub.socialfeedservice.dto.response.report.ReportDetailResponse;
import com.bondhub.socialfeedservice.model.enums.ReportStatus;
import com.bondhub.socialfeedservice.model.enums.TargetType;

import java.util.List;

public interface ReportAggregationRepository {

    PageResponse<List<ContentReportSummary>> findGroupedReports(
            ReportStatus status, int page, int size);

    List<ReportDetailResponse> findReportsByTarget(String targetId, TargetType targetType);
}
