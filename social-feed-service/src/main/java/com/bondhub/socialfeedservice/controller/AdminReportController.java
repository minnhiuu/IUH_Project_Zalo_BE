package com.bondhub.socialfeedservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.socialfeedservice.dto.request.report.BulkModerationRequest;
import com.bondhub.socialfeedservice.dto.response.report.ContentReportSummary;
import com.bondhub.socialfeedservice.dto.response.report.ReportDetailResponse;
import com.bondhub.socialfeedservice.dto.response.report.ReportResponse;
import com.bondhub.socialfeedservice.dto.response.report.UserWarningResponse;
import com.bondhub.socialfeedservice.model.enums.ReportStatus;
import com.bondhub.socialfeedservice.model.enums.TargetType;
import com.bondhub.socialfeedservice.service.report.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Reports", description = "Admin moderation APIs")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminReportController {

    ModerationService moderationService;

    @GetMapping
    @Operation(summary = "Get grouped content reports, paged, optionally filtered by overall status")
    public ResponseEntity<ApiResponse<PageResponse<List<ContentReportSummary>>>> getGroupedReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                moderationService.getGroupedReports(status, page, size)));
    }

    @GetMapping("/target/{targetType}/{targetId}")
    @Operation(summary = "Get all individual reports for a specific content item")
    public ResponseEntity<ApiResponse<List<ReportDetailResponse>>> getReportsForTarget(
            @PathVariable TargetType targetType,
            @PathVariable String targetId) {
        return ResponseEntity.ok(ApiResponse.success(
                moderationService.getReportsForTarget(targetId, targetType)));
    }

    @PostMapping("/action")
    @Operation(summary = "Bulk process all pending reports for a content item")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> processReports(
            @Valid @RequestBody BulkModerationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                moderationService.processReportsForTarget(request)));
    }

    @GetMapping("/warnings/{userId}")
    @Operation(summary = "Get warnings for a user")
    public ResponseEntity<ApiResponse<List<UserWarningResponse>>> getUserWarnings(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(moderationService.getUserWarnings(userId)));
    }
}
