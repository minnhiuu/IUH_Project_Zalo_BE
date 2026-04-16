package com.bondhub.socialfeedservice.service.report;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.socialfeedservice.dto.request.report.BulkModerationRequest;
import com.bondhub.socialfeedservice.dto.response.report.ContentReportSummary;
import com.bondhub.socialfeedservice.dto.response.report.ReportDetailResponse;
import com.bondhub.socialfeedservice.dto.response.report.ReportResponse;
import com.bondhub.socialfeedservice.dto.response.report.UserWarningResponse;
import com.bondhub.socialfeedservice.mapper.ReportMapper;
import com.bondhub.socialfeedservice.model.Comment;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.Report;
import com.bondhub.socialfeedservice.model.UserWarning;
import com.bondhub.socialfeedservice.model.enums.AdminAction;
import com.bondhub.socialfeedservice.model.enums.ReportStatus;
import com.bondhub.socialfeedservice.model.enums.TargetType;
import com.bondhub.socialfeedservice.repository.CommentRepository;
import com.bondhub.socialfeedservice.repository.PostRepository;
import com.bondhub.socialfeedservice.repository.ReportAggregationRepository;
import com.bondhub.socialfeedservice.repository.ReportRepository;
import com.bondhub.socialfeedservice.repository.UserWarningRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModerationServiceImpl implements ModerationService {

    ReportRepository reportRepository;
    PostRepository postRepository;
    CommentRepository commentRepository;
    UserWarningRepository userWarningRepository;
    ReportAggregationRepository reportAggregationRepository;
    SecurityUtil securityUtil;
    ReportMapper reportMapper;

    @Override
    @Transactional
    public List<ReportResponse> processReportsForTarget(BulkModerationRequest request) {
        String adminId = securityUtil.getCurrentUserId();

        List<Report> allReports = reportRepository.findByTargetIdAndTargetType(
                request.targetId(), request.targetType());

        List<Report> pendingReports = allReports.stream()
                .filter(r -> r.getStatus() == ReportStatus.PENDING)
                .toList();

        if (pendingReports.isEmpty()) {
            throw new AppException(ErrorCode.REPORT_NOT_FOUND);
        }

        // Perform content/user actions once â€” not repeated per report
        switch (request.action()) {
            case DELETE_CONTENT -> handleDeleteContent(request.targetId(), request.targetType());
            case HIDE_CONTENT -> handleHideContent(request.targetId(), request.targetType());
            case WARN_USER -> handleWarnUser(pendingReports.get(0), adminId, request.adminNote());
            case DISMISS_REPORT -> { /* No content action needed */ }
        }

        ReportStatus newStatus = request.action() == AdminAction.DISMISS_REPORT
                ? ReportStatus.DISMISSED
                : ReportStatus.RESOLVED;

        for (Report report : pendingReports) {
            report.setStatus(newStatus);
            report.setAdminId(adminId);
            report.setAdminNote(request.adminNote());
            report.setAdminAction(request.action());
        }

        List<Report> savedReports = reportRepository.saveAll(pendingReports);
        log.info("Bulk processed {} reports for targetId={}, action={}, admin={}",
                savedReports.size(), request.targetId(), request.action(), adminId);

        return savedReports.stream()
                .map(reportMapper::toReportResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<ContentReportSummary>> getGroupedReports(ReportStatus status, int page, int size) {
        return reportAggregationRepository.findGroupedReports(status, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportDetailResponse> getReportsForTarget(String targetId, TargetType targetType) {
        return reportAggregationRepository.findReportsByTarget(targetId, targetType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserWarningResponse> getUserWarnings(String userId) {
        return userWarningRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(reportMapper::toUserWarningResponse)
                .toList();
    }

    private void handleDeleteContent(String targetId, TargetType targetType) {
        if (targetType == TargetType.POST) {
            Post post = postRepository.findById(targetId).orElse(null);
            if (post != null) {
                post.setActive(false);
                postRepository.save(post);
                log.info("Post soft-deleted by moderation: postId={}", targetId);
            }
        } else if (targetType == TargetType.COMMENT) {
            Comment comment = commentRepository.findById(targetId).orElse(null);
            if (comment != null) {
                comment.setActive(false);
                commentRepository.save(comment);
                log.info("Comment soft-deleted by moderation: commentId={}", targetId);
            }
        }
    }

    private void handleHideContent(String targetId, TargetType targetType) {
        if (targetType == TargetType.POST) {
            Post post = postRepository.findById(targetId).orElse(null);
            if (post != null) {
                post.setHidden(true);
                postRepository.save(post);
                log.info("Post hidden by moderation: postId={}", targetId);
            }
        } else if (targetType == TargetType.COMMENT) {
            Comment comment = commentRepository.findById(targetId).orElse(null);
            if (comment != null) {
                comment.setHidden(true);
                commentRepository.save(comment);
                log.info("Comment hidden by moderation: commentId={}", targetId);
            }
        }
    }

    private void handleWarnUser(Report report, String adminId, String adminNote) {
        String contentAuthorId = resolveContentAuthorId(report.getTargetId(), report.getTargetType());
        if (contentAuthorId != null) {
            UserWarning warning = UserWarning.builder()
                    .userId(contentAuthorId)
                    .reportId(report.getId())
                    .reason(report.getReason())
                    .adminId(adminId)
                    .adminNote(adminNote)
                    .build();
            userWarningRepository.save(warning);
            log.info("User warning created: userId={}, reportId={}", contentAuthorId, report.getId());
        }
    }

    private String resolveContentAuthorId(String targetId, TargetType targetType) {
        if (targetType == TargetType.POST) {
            return postRepository.findById(targetId)
                    .map(Post::getAuthorId)
                    .orElse(null);
        } else {
            return commentRepository.findById(targetId)
                    .map(Comment::getAuthorId)
                    .orElse(null);
        }
    }
}
