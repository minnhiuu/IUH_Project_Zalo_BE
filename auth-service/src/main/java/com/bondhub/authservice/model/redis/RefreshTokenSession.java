package com.bondhub.authservice.model.redis;

import com.bondhub.authservice.enums.DeviceType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@RedisHash("refresh:session")
public class RefreshTokenSession {

    @Id
    String sessionId;

    @Indexed
    String accountId;

    @Indexed
    String deviceId;

    @Indexed
    DeviceType deviceType;

    String refreshTokenHash;
    String accessTokenJti; // JTI of the paired access token – used to blacklist on force-logout
    String userAgentHash;
    String ipHash;

    Long issuedAt;
    Long expiresAt;
    @Builder.Default
    Boolean revoked = false;

    @TimeToLive(unit = TimeUnit.SECONDS)
    Long ttl;

    public boolean isValid() {
        if (Boolean.TRUE.equals(revoked)) {
            return false;
        }
        if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
            return false;
        }
        return true;
    }
}
