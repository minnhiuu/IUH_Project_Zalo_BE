package com.bondhub.messageservice.dto.response;

import com.bondhub.messageservice.model.enums.MemberRole;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record GroupMemberListItemResponse(
        String userId,
        String fullName,
        String avatar,
        String phoneNumber,
        MemberRole role,
        OffsetDateTime joinedAt,
        boolean isFriend,
        boolean isCurrentUser
) {}
