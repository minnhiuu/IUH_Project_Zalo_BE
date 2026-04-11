package com.bondhub.messageservice.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record JoinGroupPreviewResponse(
        String conversationId,
        String groupName,
        String groupAvatar,
        int memberCount,
        String createdByName,
        List<MemberPreview> memberPreviews,
        boolean isAlreadyMember,
        boolean membershipApprovalEnabled,
        boolean hasPendingRequest
) {
    @Builder
    public record MemberPreview(String name, String avatar) {
    }
}
