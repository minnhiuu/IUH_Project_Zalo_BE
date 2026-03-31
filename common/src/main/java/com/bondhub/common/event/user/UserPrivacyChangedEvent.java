package com.bondhub.common.event.user;

import lombok.Builder;

@Builder
public record UserPrivacyChangedEvent(
    String userId,
    boolean showSeenStatus,
    Long timestamp
) {}
