package com.bondhub.authservice.dto.auth.response;

/**
 * Response DTO for registration initiation
 * Returns message indicating OTP was sent
 */
public record RegisterInitResponse(
        String message,
        String email) {
    public static RegisterInitResponse of(String message, String email) {
        return new RegisterInitResponse(message, email);
    }
}
