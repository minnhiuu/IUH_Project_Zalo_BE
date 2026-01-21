package com.bondhub.authservice.dto.auth.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
                @NotBlank(message = "Device ID is required") String deviceId,
                String refreshToken) {
}
