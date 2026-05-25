package com.bondhub.messageservice.dto.response;

import lombok.Builder;

@Builder
public record MessageSeenResponse(
        String userId,
        String fullName,
        String avatar
) {}
