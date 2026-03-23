package com.bondhub.common.dto.client.userservice.user.request;

import lombok.Builder;

import java.util.Set;

@Builder
public record UserCreateRequest(
    String accountId,
    String fullName,
    String phoneNumber,
    String role,
    Set<String> initialInterests
) {}
