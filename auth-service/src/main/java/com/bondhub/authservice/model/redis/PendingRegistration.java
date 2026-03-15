package com.bondhub.authservice.model.redis;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.concurrent.TimeUnit;

/**
 * Redis entity for storing pending registration data
 * Stores registration info between Step 1 and Step 2
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash("registration:pending")
public class PendingRegistration {

    /**
     * Email address (unique identifier)
     */
    @Id
    private String email;

    /**
     * Hashed password (for security, even in temporary storage)
     */
    private String passwordHash;

    /**
     * Full name
     */
    private String fullName;

    /**
     * Phone number (optional)
     */
    private String phoneNumber;

    /**
     * Timestamp when registration was initiated
     */
    private Long createdAt;

    /**
     * Time-to-live in seconds (Redis auto-deletion)
     * Should match or exceed OTP TTL (5 minutes)
     */
    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long ttl;
}
