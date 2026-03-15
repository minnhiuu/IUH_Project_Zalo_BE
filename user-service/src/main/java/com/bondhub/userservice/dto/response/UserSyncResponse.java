package com.bondhub.userservice.dto.response;

import lombok.Builder;

@Builder
public record UserSyncResponse(
    String id,
    String fullName,
    String avatar,
    String accountId
) {}
