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
    public void blacklistAccessToken(String jti, String accountId, String phoneNumber, long ttlSeconds, String reason) {
        if (ttlSeconds <= 0) {
            log.debug("Skipping blacklist for expired token jti={}", jti);
            return;
        }

        BlacklistedAccessToken blacklisted = BlacklistedAccessToken.builder()
                .jti(jti)
                .accountId(accountId)
                .phoneNumber(phoneNumber)
                .reason(reason)
                .blacklistedAt(System.currentTimeMillis())
                .ttl(ttlSeconds)
                .build();

        blacklistRepository.save(blacklisted);
        log.info("Access token blacklisted: jti={}, accountId={}, reason={}", jti, accountId, reason);
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        return blacklistRepository.existsByJti(jti);
    }

    @Override
    public void createRefreshSession(
            String sessionId,
            String accountId,
            String phoneNumber,
            String deviceId,
            DeviceType deviceType,
            String refreshToken,
            String accessTokenJti,
            String userAgent,
            String ipAddress,
            long ttlSeconds) {
        List<RefreshTokenSession> oldSessions = refreshSessionRepository
                .findByAccountIdAndDeviceType(accountId, deviceType);

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
                .accountId(accountId)
                .deviceId(deviceId)
                .deviceType(deviceType)
                .refreshTokenHash(hashSha256(refreshToken))
                .accessTokenJti(accessTokenJti)
                .userAgentHash(userAgent != null ? hashSha256(userAgent) : null)
                .ipHash(ipAddress != null ? hashSha256(ipAddress) : null)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .revoked(false)
                .ttl(ttlSeconds)
                .build();

        refreshSessionRepository.save(session);
        log.info("Refresh session created: sessionId={}, accountId={}, deviceType={}, deviceId={}",
                sessionId, accountId, deviceType, deviceId);
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
    public void updateSessionAccessToken(String sessionId, String accessTokenJti, Long accessTokenExpiresAt) {
        refreshSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setAccessTokenJti(accessTokenJti);
            session.setAccessTokenExpiresAt(accessTokenExpiresAt);
            refreshSessionRepository.save(session);
            log.debug("Session access token updated: sessionId={}, jti={}", sessionId, accessTokenJti);
        });
    }

    @Override
    public void revokeRefreshSession(String sessionId) {
        refreshSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setRevoked(true);
            refreshSessionRepository.save(session);
            log.info("Refresh session revoked: sessionId={}, accountId={}", sessionId, session.getAccountId());
        });
    }

    @Override
    public void revokeAndBlacklistSession(String sessionId, String accountId, long accessTokenTtlMs) {
        refreshSessionRepository.findById(sessionId).ifPresent(session -> {
            // 1. Revoke the refresh session
            session.setRevoked(true);
            refreshSessionRepository.save(session);
            log.info("Refresh session revoked (forced): sessionId={}, accountId={}", sessionId, accountId);

            // 2. Blacklist the paired access token by its stored JTI
            String jti = session.getAccessTokenJti();
            if (jti != null && !jti.isBlank() && accessTokenTtlMs > 0) {
                long ttlSeconds = accessTokenTtlMs / 1000;
                blacklistAccessToken(jti, accountId, null, ttlSeconds, "Device logout");
                log.info("Access token blacklisted on device logout: jti={}, sessionId={}", jti, sessionId);
            } else {
                log.debug("Skipping access token blacklist: jti={}, ttlMs={}", jti, accessTokenTtlMs);
            }
        });
    }

    @Override
    public int revokeAllUserRefreshSessions(String accountId) {
        List<RefreshTokenSession> sessions = refreshSessionRepository.findByAccountId(accountId);
        int count = sessions.size();

        if (count > 0) {
            sessions.forEach(session -> session.setRevoked(true));
            refreshSessionRepository.saveAll(sessions);
            log.info("Revoked {} refresh sessions for accountId={}", count, accountId);
        }

        return count;
    }

    @Override
    public List<String> revokeAllUserRefreshSessionsExcept(String accountId, String excludedSessionId) {
        List<RefreshTokenSession> sessions = refreshSessionRepository.findByAccountId(accountId);

        List<RefreshTokenSession> sessionsToRevoke = sessions.stream()
                .filter(session -> !session.getSessionId().equals(excludedSessionId))
                .filter(session -> !Boolean.TRUE.equals(session.getRevoked()))
                .toList();

        List<String> revokedSessionIds = new java.util.ArrayList<>();
        if (!sessionsToRevoke.isEmpty()) {
            sessionsToRevoke.forEach(session -> {
                session.setRevoked(true);
                revokedSessionIds.add(session.getSessionId());
            });
            refreshSessionRepository.saveAll(sessionsToRevoke);
            log.info("Revoked {} other refresh sessions for accountId={}, keeping sessionId={}",
                    revokedSessionIds.size(), accountId, excludedSessionId);
        }

        return revokedSessionIds;
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
