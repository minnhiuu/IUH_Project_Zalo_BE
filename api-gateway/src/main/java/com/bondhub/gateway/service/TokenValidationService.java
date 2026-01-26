package com.bondhub.gateway.service;

import com.bondhub.common.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenValidationService {

    private final JwtUtil jwtUtil;
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    public Mono<Boolean> validateToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            log.debug("Token failed basic JWT validation");
            return Mono.just(false);
        }

        String jti = jwtUtil.extractJti(token);
        String sessionId = jwtUtil.extractSessionId(token);

        Mono<Boolean> blacklistCheck = checkBlacklist(jti);
        Mono<Boolean> sessionCheck = checkSession(sessionId);

        return blacklistCheck.zipWith(sessionCheck)
                .map(tuple -> {
                    boolean notBlacklisted = tuple.getT1();
                    boolean sessionValid = tuple.getT2();
                    boolean isValid = notBlacklisted && sessionValid;

                    if (!isValid) {
                        if (!notBlacklisted) {
                            log.warn("Token rejected: Blacklisted access token jti={}", jti);
                        }
                        if (!sessionValid) {
                            log.warn("Token rejected: Invalid or revoked session sessionId={}", sessionId);
                        }
                    }

                    return isValid;
                })
                .onErrorResume(e -> {
                    log.error("Error validating token: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    private Mono<Boolean> checkBlacklist(String jti) {
        if (jti == null) {
            return Mono.just(true);
        }

        String blacklistKey = "blacklist:access:" + jti;
        return reactiveRedisTemplate.hasKey(blacklistKey)
                .map(exists -> !exists)
                .defaultIfEmpty(true);
    }

    private Mono<Boolean> checkSession(String sessionId) {
        if (sessionId == null) {
            return Mono.just(true);
        }

        String sessionKey = "refresh:session:" + sessionId;
        return reactiveRedisTemplate.hasKey(sessionKey)
                .flatMap(exists -> {
                    if (!exists) {
                        log.debug("Session not found: {}", sessionId);
                        return Mono.just(false);
                    }

                    return reactiveRedisTemplate.opsForHash()
                            .get(sessionKey, "revoked")
                            .map(revoked -> {
                                if (revoked != null && Boolean.parseBoolean(revoked.toString())) {
                                    log.debug("Session is revoked: {}", sessionId);
                                    return false;
                                }
                                return true;
                            })
                            .defaultIfEmpty(true);
                })
                .defaultIfEmpty(false);
    }
}
