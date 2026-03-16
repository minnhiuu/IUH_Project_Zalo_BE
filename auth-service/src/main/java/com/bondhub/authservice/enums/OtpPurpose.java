package com.bondhub.authservice.enums;

/**
 * Enum representing the purpose of an OTP (One-Time Password)
 */
public enum OtpPurpose {
    /**
     * OTP for email verification during registration
     */
    REGISTRATION,

    /**
     * OTP for password reset verification
     */
    PASSWORD_RESET
}
