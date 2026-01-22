package com.bondhub.authservice.model.redis;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.concurrent.TimeUnit;

/**
 * Redis entity for tracking OTP generation cooldown
 * Prevents rapid OTP resend requests (spam protection)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash("otp:cooldown")
public class OtpCooldown {

    /**
     * Email address (unique identifier)
     */
    @Id
    private String email;

    /**
     * Timestamp when cooldown was set
     */
    private Long cooldownSetAt;

    /**
     * Time-to-live in seconds (Redis auto-deletion)
     */
    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long ttl;
}
