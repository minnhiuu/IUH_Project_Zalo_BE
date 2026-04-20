package com.bondhub.socialfeedservice.service.report;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.socialfeedservice.dto.request.report.CreateReportRequest;
import com.bondhub.socialfeedservice.dto.response.report.ReportResponse;

import java.util.List;

public interface ReportService {

    ReportResponse createReport(CreateReportRequest request);

    PageResponse<List<ReportResponse>> getMyReports(int page, int size);
}
