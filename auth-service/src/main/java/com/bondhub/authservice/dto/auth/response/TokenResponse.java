package com.bondhub.authservice.dto.auth.response;

/**
 * Token response DTO containing JWT tokens
 */
public record TokenResponse(String accessToken, String refreshToken) {

    public static TokenResponse of(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken);
    }
}
