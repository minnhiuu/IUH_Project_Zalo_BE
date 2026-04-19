package com.bondhub.socialfeedservice.service.report;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.publisher.RawNotificationEventPublisher;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    RawNotificationEventPublisher rawNotificationEventPublisher;

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

        String authorId = resolveContentAuthorId(request.targetId(), request.targetType());
        String targetTypeLabel = request.targetType() == TargetType.POST ? "post" : "comment";
        String targetTypeLabelVi = request.targetType() == TargetType.POST ? "bài viết" : "bình luận";

        // Perform content/user actions once — not repeated per report
        switch (request.action()) {
            case DELETE_CONTENT -> {
                handleDeleteContent(request.targetId(), request.targetType());
                if (authorId != null) {
                    publishModerationNotification(authorId, adminId, NotificationType.CONTENT_REMOVED,
                            request.targetId(), Map.of("targetType", targetTypeLabel, "targetTypeVi", targetTypeLabelVi,
                                    "referenceId", request.targetId()));
                }
            }
            case HIDE_CONTENT -> {
                handleHideContent(request.targetId(), request.targetType());
                if (authorId != null) {
                    publishModerationNotification(authorId, adminId, NotificationType.CONTENT_HIDDEN,
                            request.targetId(), Map.of("targetType", targetTypeLabel, "targetTypeVi", targetTypeLabelVi,
                                    "referenceId", request.targetId()));
                }
            }
            case WARN_USER -> {
                UserWarning warning = handleWarnUser(pendingReports.get(0), adminId, request.adminNote(), authorId);
                if (warning != null) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("reason", pendingReports.get(0).getReason().name());
                    if (request.adminNote() != null && !request.adminNote().isBlank()) {
                        payload.put("adminNote", request.adminNote());
                    }
                    publishModerationNotification(warning.getUserId(), adminId, NotificationType.USER_WARNED,
                            warning.getId(), payload);
                }
            }
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

    private UserWarning handleWarnUser(Report report, String adminId, String adminNote, String contentAuthorId) {
        if (contentAuthorId == null) return null;
        UserWarning warning = UserWarning.builder()
                .userId(contentAuthorId)
                .reportId(report.getId())
                .reason(report.getReason())
                .adminId(adminId)
                .adminNote(adminNote)
                .build();
        UserWarning saved = userWarningRepository.save(warning);
        log.info("User warning created: userId={}, reportId={}", contentAuthorId, report.getId());
        return saved;
    }

    private void publishModerationNotification(String recipientId, String adminId, NotificationType type,
                                                String referenceId, Map<String, Object> payload) {
        try {
            RawNotificationEvent event = RawNotificationEvent.builder()
                    .recipientId(recipientId)
                    .actorId(adminId)
                    .actorName("Admin")
                    .type(type)
                    .referenceId(referenceId)
                    .payload(payload)
                    .occurredAt(LocalDateTime.now())
                    .build();
            rawNotificationEventPublisher.publish(event);
        } catch (Exception e) {
            log.warn("[Moderation] Failed to publish notification: type={}, recipient={}", type, recipientId, e);
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
