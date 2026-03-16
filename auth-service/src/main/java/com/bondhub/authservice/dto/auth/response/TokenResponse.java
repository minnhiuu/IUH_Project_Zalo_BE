package com.bondhub.authservice.dto.auth.response;

/**
 * Token response DTO containing JWT tokens
 */
public record TokenResponse(String accessToken, String refreshToken, long refreshTokenExpirationMs) {

    public static TokenResponse of(String accessToken, String refreshToken, long refreshTokenExpirationMs) {
        return new TokenResponse(accessToken, refreshToken, refreshTokenExpirationMs);
    }
}
