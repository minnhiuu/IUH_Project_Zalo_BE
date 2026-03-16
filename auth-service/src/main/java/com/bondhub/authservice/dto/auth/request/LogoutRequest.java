package com.bondhub.authservice.dto.auth.request;

/**
 * Logout request DTO
 */
public record LogoutRequest(
                String refreshToken) {
}
