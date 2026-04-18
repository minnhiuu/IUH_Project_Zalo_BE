package com.bondhub.messageservice.dto.response;

import com.bondhub.messageservice.model.enums.MemberRole;
import lombok.Builder;

@Builder
public record AdminMemberResponse(
        String userId,
        String fullName,
        String avatar,
        MemberRole role
) {}
