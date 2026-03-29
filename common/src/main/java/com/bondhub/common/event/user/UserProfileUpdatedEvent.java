package com.bondhub.common.event.user;

import lombok.Builder;

@Builder
public record UserProfileUpdatedEvent(
    String userId,
    String fullName,
    String avatar,
    Long timestamp
) {}
