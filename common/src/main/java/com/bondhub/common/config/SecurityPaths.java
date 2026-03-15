package com.bondhub.common.config;

import java.util.List;

/**
 * Security paths configuration
 * Centralized list of public paths that don't require authentication
 */
public class SecurityPaths {

    /**
     * Public paths that don't require JWT authentication at the gateway level
     * These paths include the /api prefix as they appear to the gateway
     */
    public static final List<String> PUBLIC_PATHS = List.of(
            // Authentication endpoints
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/register/verify",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/refresh",
            "/api/auth/validate",
            "/api/auth/qr/generate",
            "/api/auth/qr/wait/**",

            // Internal service-to-service endpoints
            "/api/users/internal/**",

            // Test endpoints
            "/api/users/test/security/public",
            "/api/users/qr-info",

            // Swagger UI and API documentation
            "/swagger-ui",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/api/auth/v3/api-docs",
            "/api/users/v3/api-docs",
            "/api/messages/v3/api-docs",
            "/api/notifications/v3/api-docs",
            // Service-level API docs paths (after gateway routes them)
            "/auth/v3/api-docs",
            "/auth/v3/api-docs/**",
            "/user/v3/api-docs",
            "/user/v3/api-docs/**",
            "/message/v3/api-docs",
            "/message/v3/api-docs/**",
            "/notification/v3/api-docs",
            "/notification/v3/api-docs/**",
            "/file/v3/api-docs",
            "/file/v3/api-docs/**",
            "/friend/v3/api-docs",
            "/friend/v3/api-docs/**",

            // Actuator endpoints
            "/actuator/health",
            "/actuator/info");

    /**
     * Public paths for services (after gateway rewrite)
     * These paths are as they appear to services after the gateway rewrites them
     */
    public static final List<String> SERVICE_PUBLIC_PATHS = List.of(
            // Auth endpoints (gateway strips /api/auth -> /auth)
            "/auth/login",
            "/auth/register",
            "/auth/register/verify",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/refresh",
            "/auth/validate",
            "/auth/logout",
            "/auth/qr/generate",
            "/auth/qr/wait/**",

            // Test endpoints (gateway strips /api/users -> /users)
            "/users/test/security/public",

            // Swagger UI and API documentation
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui",
            "/swagger-ui/**",

            // Actuator endpoints
            "/actuator/health",
            "/actuator/info");

    /**
     * Paths that should be accessible only by internal services (not through
     * gateway)
     */
    public static final List<String> INTERNAL_PATHS = List.of(
            "/actuator/**");

    private SecurityPaths() {
        // Utility class - prevent instantiation
    }

    /**
     * Check if a path is public at gateway level (doesn't require authentication)
     */
    public static boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream()
                .anyMatch(pattern -> matchesPattern(path, pattern));
    }

    /**
     * Check if a path is public at service level (after gateway rewrite)
     */
    public static boolean isServicePublicPath(String path) {
        return SERVICE_PUBLIC_PATHS.stream()
                .anyMatch(pattern -> matchesPattern(path, pattern));
    }

    /**
     * Check if a path is internal (only accessible by services)
     */
    public static boolean isInternalPath(String path) {
        return INTERNAL_PATHS.stream()
                .anyMatch(pattern -> matchesPattern(path, pattern));
    }

    /**
     * Simple pattern matching for paths with wildcards
     */
    private static boolean matchesPattern(String path, String pattern) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        return path.equals(pattern);
    }
}
