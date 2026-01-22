package com.bondhub.common.utils;

import com.bondhub.common.config.JwtProperties;
import com.bondhub.common.enums.Role;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Utility class for token generation, validation, and extraction
 * Used by both auth-service and api-gateway
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtUtil {

    private final JwtProperties jwtProperties;

    /**
     * Generate access token with user information
     *
     * @param userId User ID
     * @param email  User email
     * @param role   User role
     * @return JWT access token
     */
    public String generateAccessToken(String userId, String email, Role role, String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("sessionId", sessionId);
        claims.put("jti", java.util.UUID.randomUUID().toString());
        // Store single role as string in JWT claims
        claims.put("role", role != null ? role.getName() : null);
        claims.put("type", "access");

        return generateToken(claims, userId, jwtProperties.getAccessTokenExpiration());
    }

    /**
     * Generate refresh token
     *
     * @param userId       User ID
     * @param sessionId    Session ID for device tracking
     * @param expirationMs Expiration time for this specific token
     * @return JWT refresh token
     */
    public String generateRefreshToken(String userId, String sessionId, long expirationMs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("sessionId", sessionId);
        claims.put("jti", java.util.UUID.randomUUID().toString());

        return generateToken(claims, userId, expirationMs);
    }

    /**
     * Generate JWT token with claims
     *
     * @param claims     Extra claims
     * @param subject    Subject (usually userId)
     * @param expiration Expiration time in milliseconds
     * @return JWT token
     */
    private String generateToken(Map<String, Object> claims, String subject, Long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validate JWT token
     *
     * @param token JWT token
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Extract user ID from token
     *
     * @param token JWT token
     * @return User ID
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract email from token
     *
     * @param token JWT token
     * @return Email
     */
    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    /**
     * Extract role from token
     *
     * @param token JWT token
     * @return Role string, or null if not present
     */
    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }

    /**
     * Check if token is expired
     *
     * @param token JWT token
     * @return true if expired, false otherwise
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractClaim(token, Claims::getExpiration);
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * Extract token type from token
     *
     * @param token JWT token
     * @return Token type ("access" or "refresh")
     */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    /**
     * Validate if token is a refresh token
     *
     * @param token JWT token
     * @return true if token is a refresh token, false otherwise
     */
    public boolean isRefreshToken(String token) {
        try {
            String tokenType = extractTokenType(token);
            return "refresh".equals(tokenType);
        } catch (Exception e) {
            log.error("Error checking token type: {}", e.getMessage());
            return false;
        }
    }

    public String extractJti(String token) {
        return extractClaim(token, claims -> claims.get("jti", String.class));
    }

    public String extractSessionId(String token) {
        return extractClaim(token, claims -> claims.get("sessionId", String.class));
    }

    public long getRemainingTtl(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        long remainingMs = expiration.getTime() - System.currentTimeMillis();
        return Math.max(0, remainingMs / 1000);
    }

    public long getWebRefreshExpirationMs() {
        return jwtProperties.getRefreshExpirationWeb();
    }

    public long getMobileRefreshExpirationMs() {
        return jwtProperties.getRefreshExpirationMobile();
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.getAccessTokenExpiration() / 1000;
    }

    /**
     * Extract specific claim from token
     *
     * @param token          JWT token
     * @param claimsResolver Function to extract claim
     * @param <T>            Claim type
     * @return Claim value
     */
    private <T> T extractClaim(String token, java.util.function.Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from token
     *
     * @param token JWT token
     * @return Claims
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get signing key from secret
     *
     * @return SecretKey
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
