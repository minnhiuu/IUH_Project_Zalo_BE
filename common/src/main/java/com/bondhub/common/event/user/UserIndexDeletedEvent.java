package com.bondhub.common.event.user;

import lombok.Builder;

@Builder
public record UserIndexDeletedEvent (
        String userId,
        Long timestamp
) {}
