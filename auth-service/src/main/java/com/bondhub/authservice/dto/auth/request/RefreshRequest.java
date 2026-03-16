package com.bondhub.authservice.dto.auth.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
                @NotBlank(message = "{validation.deviceId.required}") String deviceId,
                String refreshToken) {
}
