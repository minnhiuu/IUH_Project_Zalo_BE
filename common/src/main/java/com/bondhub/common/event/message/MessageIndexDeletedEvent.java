package com.bondhub.common.event.message;

import lombok.Builder;

@Builder
public record MessageIndexDeletedEvent(
        String messageId
) {}
