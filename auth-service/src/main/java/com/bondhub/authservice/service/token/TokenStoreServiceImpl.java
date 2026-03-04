package com.bondhub.authservice.service.token;

import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.model.redis.BlacklistedAccessToken;
import com.bondhub.authservice.model.redis.RefreshTokenSession;
import com.bondhub.authservice.repository.redis.BlacklistedAccessTokenRepository;
import com.bondhub.authservice.repository.redis.RefreshTokenSessionRepository;
import com.bondhub.common.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenStoreServiceImpl implements TokenStoreService {

    private final BlacklistedAccessTokenRepository blacklistRepository;
    private final RefreshTokenSessionRepository refreshSessionRepository;
    private final JwtUtil jwtUtil;

    @Override
    public void blacklistAccessToken(String jti, String userId, String phoneNumber, long ttlSeconds, String reason) {
        if (ttlSeconds <= 0) {
            log.debug("Skipping blacklist for expired token jti={}", jti);
            return;
        }

        BlacklistedAccessToken blacklisted = BlacklistedAccessToken.builder()
                .jti(jti)
                .userId(userId)
                .phoneNumber(phoneNumber)
                .reason(reason)
                .blacklistedAt(System.currentTimeMillis())
                .ttl(ttlSeconds)
                .build();

        blacklistRepository.save(blacklisted);
        log.info("Access token blacklisted: jti={}, userId={}, reason={}", jti, userId, reason);
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        return blacklistRepository.existsByJti(jti);
    }

    @Override
    public void createRefreshSession(
            String sessionId,
            String userId,
            String phoneNumber,
            String deviceId,
            DeviceType deviceType,
            String refreshToken,
            String userAgent,
            String ipAddress,
            long ttlSeconds) {
        List<RefreshTokenSession> oldSessions = refreshSessionRepository
                .findByUserIdAndDeviceType(userId, deviceType);

        for (RefreshTokenSession oldSession : oldSessions) {
            if (!deviceId.equals(oldSession.getDeviceId())) {
                log.info("Kicking session from different device: sessionId={}, oldDeviceId={}, newDeviceId={}",
                        oldSession.getSessionId(), oldSession.getDeviceId(), deviceId);
                refreshSessionRepository.delete(oldSession);
            } else {
                log.info("Same device re-login detected: updating session sessionId={}, deviceId={}",
                        oldSession.getSessionId(), deviceId);
                refreshSessionRepository.delete(oldSession);
            }
        }

        long now = System.currentTimeMillis();
        long expiresAt = now + (ttlSeconds * 1000);

        RefreshTokenSession session = RefreshTokenSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .phoneNumber(phoneNumber)
                .deviceId(deviceId)
                .deviceType(deviceType)
                .refreshTokenHash(hashSha256(refreshToken))
                .userAgentHash(userAgent != null ? hashSha256(userAgent) : null)
                .ipHash(ipAddress != null ? hashSha256(ipAddress) : null)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .revoked(false)
                .ttl(ttlSeconds)
                .build();

        refreshSessionRepository.save(session);
        log.info("Refresh session created: sessionId={}, userId={}, deviceType={}, deviceId={}",
                sessionId, userId, deviceType, deviceId);
    }

    @Override
    public Optional<RefreshTokenSession> findRefreshSession(String sessionId) {
        return refreshSessionRepository.findById(sessionId);
    }

    @Override
    public boolean validateRefreshSession(String sessionId, String refreshToken) {
        return refreshSessionRepository.findById(sessionId)
                .map(session -> {
                    if (Boolean.TRUE.equals(session.getRevoked())) {
                        log.warn("Refresh session revoked: sessionId={}", sessionId);
                        return false;
                    }

                    if (!session.isValid()) {
                        log.warn("Refresh session expired: sessionId={}", sessionId);
                        return false;
                    }

                    String providedHash = hashSha256(refreshToken);
                    if (!providedHash.equals(session.getRefreshTokenHash())) {
                        log.warn("Refresh token hash mismatch: sessionId={}", sessionId);
                        return false;
                    }

                    return true;
                })
                .orElse(false);
    }

    @Override
    public boolean validateRefreshSessionWithBinding(
            String sessionId,
            String refreshToken,
            String deviceId,
            String userAgent,
            String ipAddress) {
        return refreshSessionRepository.findById(sessionId)
                .map(session -> {
                    if (Boolean.TRUE.equals(session.getRevoked())) {
                        log.warn("[SECURITY] Session revoked: sessionId={}", sessionId);
                        return false;
                    }

                    if (!jwtUtil.isRefreshToken(refreshToken)) {
                        log.warn("The request token is not a refresh token: {}", refreshToken);
                        return false;
                    }

                    if (!session.isValid()) {
                        log.warn("Session expired: sessionId={}", sessionId);
                        return false;
                    }

                    String providedTokenHash = hashSha256(refreshToken);
                    if (!providedTokenHash.equals(session.getRefreshTokenHash())) {
                        log.warn("[SECURITY] Token hash mismatch: sessionId={}", sessionId);
                        return false;
                    }

                    if (!deviceId.equals(session.getDeviceId())) {
                        log.error("[SECURITY ALERT] Device mismatch! sessionId={}, expected={}, got={}",
                                sessionId, session.getDeviceId(), deviceId);
                        return false;
                    }

                    if (session.getUserAgentHash() != null && userAgent != null) {
                        String providedUaHash = hashSha256(userAgent);
                        if (!providedUaHash.equals(session.getUserAgentHash())) {
                            log.warn("[SECURITY] User-Agent changed: sessionId={}", sessionId);
                        }
                    }

                    if (session.getIpHash() != null && ipAddress != null) {
                        String providedIpHash = hashSha256(ipAddress);
                        if (!providedIpHash.equals(session.getIpHash())) {
                            log.info("IP changed for sessionId={}, updating", sessionId);
                            session.setIpHash(providedIpHash);
                            refreshSessionRepository.save(session);
                        }
                    }

                    return true;
                })
                .orElse(false);
    }

    @Override
    public void revokeRefreshSession(String sessionId) {
        refreshSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setRevoked(true);
            refreshSessionRepository.save(session);
            log.info("Refresh session revoked: sessionId={}, userId={}", sessionId, session.getUserId());
        });
    }

    @Override
    public int revokeAllUserRefreshSessions(String userId) {
        List<RefreshTokenSession> sessions = refreshSessionRepository.findByUserId(userId);
        int count = sessions.size();

        if (count > 0) {
            sessions.forEach(session -> session.setRevoked(true));
            refreshSessionRepository.saveAll(sessions);
            log.info("Revoked {} refresh sessions for userId={}", count, userId);
        }

        return count;
    }

    @Override
    public String hashSha256(String input) {
        if (input == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
