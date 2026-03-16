package com.bondhub.authservice.model.redis;

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
@RedisHash("blacklist:access")
public class BlacklistedAccessToken {

    @Id
    private String jti;

    @Indexed
    private String accountId;

    @Indexed
    private String phoneNumber;

    private String reason;

    private Long blacklistedAt;

    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long ttl;
}
