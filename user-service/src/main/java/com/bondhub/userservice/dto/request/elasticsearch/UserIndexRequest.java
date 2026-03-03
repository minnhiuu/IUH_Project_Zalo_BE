package com.bondhub.userservice.dto.request.elasticsearch;

import com.bondhub.common.enums.Role;
import lombok.Builder;

@Builder
public record UserIndexRequest(
        String userId,
        String accountId,
        String fullName,
        String avatar,
        String phoneNumber,
        Role role
) {
}
