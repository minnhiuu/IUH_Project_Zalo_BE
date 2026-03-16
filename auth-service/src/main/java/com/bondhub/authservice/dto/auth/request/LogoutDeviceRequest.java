package com.bondhub.authservice.dto.auth.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Logout specific device request DTO
 */
public record LogoutDeviceRequest(
        @NotBlank(message = "Session ID is required") String sessionId,

        String refreshToken) {
}
