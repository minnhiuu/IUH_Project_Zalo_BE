package com.bondhub.authservice.model.redis;

import com.bondhub.authservice.enums.OtpPurpose;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.util.concurrent.TimeUnit;

/**
 * Redis entity for storing OTP information
 * Uses email as unique identifier
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash("otp:record")
public class OtpRecord {

    /**
     * Email address (unique identifier)
     */
    @Id
    private String email;

    /**
     * SHA-256 hash of the OTP code (never store plaintext)
     */
    private String otpHash;

    /**
     * Purpose of the OTP
     */
    @Indexed
    private OtpPurpose purpose;

    /**
     * Number of failed validation attempts
     */
    @Builder.Default
    private Integer attempts = 0;

    /**
     * Timestamp when OTP was created
     */
    private Long createdAt;

    /**
     * Timestamp when OTP expires
     */
    private Long expiresAt;

    /**
     * Time-to-live in seconds (Redis auto-deletion)
     */
    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long ttl;

    /**
     * Check if OTP is still valid (not expired)
     */
    public boolean isValid() {
        if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
            return false;
        }
        return true;
    }
}
