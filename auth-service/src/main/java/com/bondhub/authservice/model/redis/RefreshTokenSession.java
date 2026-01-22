package com.bondhub.authservice.model.redis;

import com.bondhub.authservice.enums.DeviceType;
import lombok.*;
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
@RedisHash("refresh:session")
public class RefreshTokenSession {

    @Id
    private String sessionId;

    @Indexed
    private String userId;

    @Indexed
    private String phoneNumber;

    @Indexed
    private String deviceId;

    @Indexed
    private DeviceType deviceType;

    private String refreshTokenHash;
    private String userAgentHash;
    private String ipHash;

    private Long issuedAt;
    private Long expiresAt;

    @Builder.Default
    private Boolean revoked = false;

    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long ttl;

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
