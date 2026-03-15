package com.bondhub.authservice.service.otp;

import com.bondhub.authservice.enums.OtpPurpose;

/**
 * Service for OTP (One-Time Password) generation and validation
 */
public interface OtpService {

    /**
     * Generate a new OTP and store it in Redis
     *
     * @param email   Recipient email address
     * @param purpose Purpose of the OTP (REGISTRATION or PASSWORD_RESET)
     * @return The generated OTP code (plaintext, for sending via email)
     */
    String generateAndStoreOtp(String email, OtpPurpose purpose);

    /**
     * Validate an OTP against the stored hash
     * <p>
     * Implements rate limiting (max 5 attempts).
     * Deletes OTP after successful validation (one-time use).
     * </p>
     *
     * @param email   Email address
     * @param otp     OTP code to validate
     * @param purpose Expected purpose of the OTP
     * @return true if OTP is valid, false otherwise
     */
    boolean validateOtp(String email, String otp, OtpPurpose purpose);

    /**
     * Manually invalidate/delete an OTP from Redis
     *
     * @param email Email address
     */
    void invalidateOtp(String email);

    /**
     * Hash an OTP using SHA-256
     *
     * @param otp OTP to hash
     * @return SHA-256 hash of the OTP
     */
    String hashOtp(String otp);
}
