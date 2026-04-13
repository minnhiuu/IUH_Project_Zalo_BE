package com.bondhub.messageservice.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record LinkPreviewResponse(
        String url,
        String token,
        String groupName,
        String groupAvatar,
        int memberCount,
        List<MemberSnapshot> memberPreviews
) {
    @Builder
    public record MemberSnapshot(String name, String avatar) {
    }
}
