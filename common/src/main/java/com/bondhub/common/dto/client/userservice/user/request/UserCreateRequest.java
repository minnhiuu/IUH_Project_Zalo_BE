package com.bondhub.common.dto.client.userservice.user.request;

import lombok.Builder;

@Builder
public record UserCreateRequest(
    String accountId,
    String fullName
) {}
