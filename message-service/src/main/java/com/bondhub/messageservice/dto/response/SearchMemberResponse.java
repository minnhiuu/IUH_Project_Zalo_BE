package com.bondhub.messageservice.dto.response;

import lombok.Builder;

@Builder
public record SearchMemberResponse(
        String userId,
        String fullName,
        String avatar,
        String phoneNumber,
        boolean isAlreadyMember
) {}