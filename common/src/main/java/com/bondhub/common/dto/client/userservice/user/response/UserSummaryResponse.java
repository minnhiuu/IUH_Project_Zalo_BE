package com.bondhub.common.dto.client.userservice.user.response;

import lombok.Builder;

@Builder
public record UserSummaryResponse(
    String id,
    String fullName,
    String avatar
) {}
