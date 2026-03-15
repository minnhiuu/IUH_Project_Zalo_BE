package com.bondhub.authservice.service.token;

import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.model.redis.RefreshTokenSession;

import java.util.Optional;

public interface TokenStoreService {

        /**
         * Blacklist an access token (JTI)
         *
         * @param jti         JWT ID
         * @param accountId   Account ID associated with the token
         * @param phoneNumber User phone number (for tracking)
         * @param ttlSeconds  Time to live in seconds (remaining validity of the token)
         * @param reason      Reason for blacklisting (e.g., "Logout", "Security Alert")
         */
        void blacklistAccessToken(String jti, String accountId, String phoneNumber, long ttlSeconds, String reason);

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
         * @param accountId    Account ID
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
                        String accountId,
                        String phoneNumber,
                        String deviceId,
                        DeviceType deviceType,
                        String refreshToken,
                        String accessTokenJti,
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
         * Revoke a refresh session AND blacklist its paired access token.
         * <p>
         * Use this instead of {@link #revokeRefreshSession(String)} when forcing a
         * device logout so the still-valid access token is invalidated immediately.
         * </p>
         *
         * @param sessionId        Session ID to revoke
         * @param accountId        Account ID (for blacklist metadata)
         * @param accessTokenTtlMs Remaining TTL of the access token in milliseconds
         *                         (pass 0 or negative to skip blacklisting expired
         *                         tokens)
         */
        void revokeAndBlacklistSession(String sessionId, String accountId, long accessTokenTtlMs);

        /**
         * Update the access token info stored in a session (called after token generation)
         *
         * @param sessionId            Session ID
         * @param accessTokenJti       JTI of the issued access token
         * @param accessTokenExpiresAt Expiry timestamp (ms since epoch) of the access token
         */
        void updateSessionAccessToken(String sessionId, String accessTokenJti, Long accessTokenExpiresAt);

        /**
         * Revoke all refresh sessions for a user (e.g., "Logout all devices")
         *
         * @param accountId Account ID
         * @return Number of sessions revoked
         */
        int revokeAllUserRefreshSessions(String accountId);

        /**
         * Revoke all refresh sessions for a user EXCEPT the current one
         *
         * @param accountId         Account ID
         * @param excludedSessionId The session ID to keep active
         * @return List of revoked session IDs
         */
        java.util.List<String> revokeAllUserRefreshSessionsExcept(String accountId, String excludedSessionId);

        /**
         * Hash a string using SHA-256
         *
         * @param input Input string
         * @return SHA-256 hash
         */
        String hashSha256(String input);
}
