package com.bondhub.common.event.user;

import com.bondhub.common.enums.Role;
import lombok.Builder;


@Builder
public record UserIndexRequestedEvent (
        String userId,
        String fullName,
        String avatar,

        String accountId,
        String phoneNumber,
        Role role,

        Long timestamp
) {}
