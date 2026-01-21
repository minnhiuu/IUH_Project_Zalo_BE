package com.bondhub.authservice.service.token;

import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.model.RefreshTokenSession;

import java.util.Optional;

public interface TokenStoreService {

    void blacklistAccessToken(String jti, String userId, String phoneNumber, long ttlSeconds, String reason);

    boolean isAccessTokenBlacklisted(String jti);

    void createRefreshSession(
            String sessionId,
            String userId,
            String phoneNumber,
            String deviceId,
            DeviceType deviceType,
            String refreshToken,
            String userAgent,
            String ipAddress,
            long ttlSeconds);

    Optional<RefreshTokenSession> findRefreshSession(String sessionId);

    boolean validateRefreshSession(String sessionId, String refreshToken);

    boolean validateRefreshSessionWithBinding(
            String sessionId,
            String refreshToken,
            String deviceId,
            String userAgent,
            String ipAddress);

    void revokeRefreshSession(String sessionId);

    int revokeAllUserRefreshSessions(String userId);

    String hashSha256(String input);
}
