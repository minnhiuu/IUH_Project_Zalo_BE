package com.bondhub.authservice.service.token;

import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.model.redis.RefreshTokenSession;

import java.util.Optional;

public interface TokenStoreService {

        /**
         * Blacklist an access token (JTI)
         *
         * @param jti         JWT ID
         * @param userId      User ID associated with the token
         * @param phoneNumber User phone number (for tracking)
         * @param ttlSeconds  Time to live in seconds (remaining validity of the token)
         * @param reason      Reason for blacklisting (e.g., "Logout", "Security Alert")
         */
        void blacklistAccessToken(String jti, String userId, String phoneNumber, long ttlSeconds, String reason);

        /**
         * Check if an access token is blacklisted
         *
         * @param jti JWT ID
         * @return true if blacklisted, false otherwise
         */
        boolean isAccessTokenBlacklisted(String jti);

        /**
         * Create a new refresh token session
         *
         * @param sessionId    Unique session ID
         * @param userId       User ID
         * @param phoneNumber  User phone number
         * @param deviceId     Device ID (from client)
         * @param deviceType   Device type (WEB, MOBILE, etc.)
         * @param refreshToken The refresh token string (hashed before storage)
         * @param userAgent    User Agent string
         * @param ipAddress    IP address of the client
         * @param ttlSeconds   Time to live in seconds
         */
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

        /**
         * Find a refresh session by its ID
         *
         * @param sessionId Session ID
         * @return Optional containing the session if found
         */
        Optional<RefreshTokenSession> findRefreshSession(String sessionId);

        /**
         * Validate a refresh session (basic check)
         *
         * @param sessionId    Session ID
         * @param refreshToken Refresh token to validate against stored hash
         * @return true if valid, false otherwise
         */
        boolean validateRefreshSession(String sessionId, String refreshToken);

        /**
         * Validate a refresh session with strict device binding checks
         *
         * @param sessionId    Session ID
         * @param refreshToken Refresh token
         * @param deviceId     Device ID (must match stored)
         * @param userAgent    User Agent (must match stored mostly)
         * @param ipAddress    IP Address (optional check, usually just logged)
         * @return true if valid and bound to correct device
         */
        boolean validateRefreshSessionWithBinding(
                        String sessionId,
                        String refreshToken,
                        String deviceId,
                        String userAgent,
                        String ipAddress);

        /**
         * Revoke (delete) a refresh session
         *
         * @param sessionId Session ID to revoke
         */
        void revokeRefreshSession(String sessionId);

        /**
         * Revoke all refresh sessions for a user (e.g., "Logout all devices")
         *
         * @param userId User ID
         * @return Number of sessions revoked
         */
        int revokeAllUserRefreshSessions(String userId);

        /**
         * Hash a string using SHA-256
         *
         * @param input Input string
         * @return SHA-256 hash
         */
        String hashSha256(String input);
}
