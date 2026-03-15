package com.bondhub.common.dto.client.userservice.user.request;

import com.bondhub.common.enums.Role;
import lombok.Builder;

@Builder
public record UserIndexRequest(
        String userId,
        String phoneNumber,
        Role role
) {}
