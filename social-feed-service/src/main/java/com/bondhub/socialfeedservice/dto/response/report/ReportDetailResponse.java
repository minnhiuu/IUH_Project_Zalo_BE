package com.bondhub.socialfeedservice.dto.response.report;

import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import com.bondhub.socialfeedservice.model.enums.AdminAction;
import com.bondhub.socialfeedservice.model.enums.ReportReason;
import com.bondhub.socialfeedservice.model.enums.ReportStatus;
import com.bondhub.socialfeedservice.model.enums.TargetType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ReportDetailResponse(
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
        LocalDateTime updatedAt,
        String contentText,
        List<String> contentMediaUrls,
        String contentAuthorId,
        AuthorInfo contentAuthorInfo,
        AuthorInfo reporterInfo
) {
}
