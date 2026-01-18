package com.bondhub.authservice.dto.auth.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Logout request DTO
 */
public record LogoutRequest(
        @NotBlank(message = "Refresh token is required") String refreshToken) {
}
