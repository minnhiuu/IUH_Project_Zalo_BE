package com.bondhub.userservice.dto.response.auth;

public record DeviceSessionResponse(
        String id,
        String deviceId,
        String sessionId
) {
}
