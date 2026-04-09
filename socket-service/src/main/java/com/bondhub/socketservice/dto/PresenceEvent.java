package com.bondhub.socketservice.dto;

import com.bondhub.common.enums.Status;
import lombok.Builder;

@Builder
public record PresenceEvent(
        String userId,
        Status status) {
}
