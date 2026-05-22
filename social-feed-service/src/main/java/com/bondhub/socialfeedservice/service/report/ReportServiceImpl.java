package com.bondhub.socialfeedservice.service.report;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.socialfeedservice.dto.request.report.CreateReportRequest;
import com.bondhub.socialfeedservice.dto.response.report.ReportResponse;
import com.bondhub.socialfeedservice.mapper.ReportMapper;
import com.bondhub.socialfeedservice.model.Comment;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.Report;
import com.bondhub.socialfeedservice.model.enums.ReportStatus;
import com.bondhub.socialfeedservice.model.enums.TargetType;
import com.bondhub.socialfeedservice.repository.CommentRepository;
import com.bondhub.socialfeedservice.repository.PostRepository;
import com.bondhub.socialfeedservice.repository.ReportRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportServiceImpl implements ReportService {

    ReportRepository reportRepository;
    PostRepository postRepository;
    CommentRepository commentRepository;
    SecurityUtil securityUtil;
    ReportMapper reportMapper;

    @Override
    @Transactional
    public ReportResponse createReport(CreateReportRequest request) {
        String reporterId = securityUtil.getCurrentUserId();

        // Validate target exists and get author ID
        String contentAuthorId = validateTargetAndGetAuthorId(request.targetId(), request.targetType());

        // Self-report check
        if (reporterId.equals(contentAuthorId)) {
            throw new AppException(ErrorCode.REPORT_SELF_CONTENT);
        }

        // Duplicate PENDING report check
        reportRepository.findByReporterIdAndTargetIdAndTargetTypeAndStatus(
                reporterId, request.targetId(), request.targetType(), ReportStatus.PENDING
        ).ifPresent(existing -> {
            throw new AppException(ErrorCode.REPORT_ALREADY_PENDING);
        });

        Report report = Report.builder()
                .reporterId(reporterId)
                .targetId(request.targetId())
                .targetType(request.targetType())
                .reason(request.reason())
                .details(request.details())
                .status(ReportStatus.PENDING)
                .build();

        Report savedReport = reportRepository.save(report);
        log.info("Report created: id={}, reporter={}, target={}, type={}",
                savedReport.getId(), reporterId, request.targetId(), request.targetType());

        return reportMapper.toReportResponse(savedReport);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<ReportResponse>> getMyReports(int page, int size) {
        String reporterId = securityUtil.getCurrentUserId();
        Page<Report> reportPage = reportRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, size));

        // Filter by reporterId in the page (or add a dedicated repo method)
        return PageResponse.fromPage(reportPage, reportMapper::toReportResponse);
    }

    private String validateTargetAndGetAuthorId(String targetId, TargetType targetType) {
        return switch (targetType) {
            case POST -> {
                Post post = postRepository.findByIdAndActiveTrueAndIsCurrentTrueAndHiddenFalse(targetId)
                        .orElseThrow(() -> new AppException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                yield post.getAuthorId();
            }
            case COMMENT -> {
                Comment comment = commentRepository.findByIdAndActiveTrueAndHiddenFalse(targetId)
                        .orElseThrow(() -> new AppException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                yield comment.getAuthorId();
            }
        };
    }
}
