package com.bondhub.authservice.dto.auth.response;

public record ForgotPasswordResponse(
        String message,
        String email) {
    public static ForgotPasswordResponse of(String email) {
        return new ForgotPasswordResponse(
                "Password reset OTP has been sent to your email.",
                email);
    }
}
