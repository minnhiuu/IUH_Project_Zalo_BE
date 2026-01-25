package com.bondhub.authservice.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Token response DTO containing JWT tokens
 */
public record TokenResponse(String accessToken, @JsonIgnore String refreshToken) {

    public static TokenResponse of(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken);
    }
}
