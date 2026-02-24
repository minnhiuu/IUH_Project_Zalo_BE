package com.bondhub.userservice.dto.request;

import com.bondhub.common.enums.Role;
import lombok.Builder;

@Builder
public record UserIndexRequest(
        String userId,
        String phoneNumber,
        Role role
) {
}
