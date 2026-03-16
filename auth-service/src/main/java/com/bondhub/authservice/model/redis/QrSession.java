package com.bondhub.authservice.model.redis;

import com.bondhub.authservice.enums.QrSessionStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
import java.time.LocalDateTime;

@RedisHash(value = "qr_sessions")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QrSession implements Serializable {

    @Id
    String id;
    QrSessionStatus status;
    String accountId;
    String userAvatar;
    String userFullName;
    String webAccessToken;
    String webRefreshToken;
    Long refreshTokenExpirationMs;
    String ipAddress;
    String userAgent;
    String deviceId;
    
    LocalDateTime expiresAt;

    @TimeToLive
    Long ttl;
}
