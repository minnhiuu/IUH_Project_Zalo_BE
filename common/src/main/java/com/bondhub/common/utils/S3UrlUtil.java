package com.bondhub.common.utils;

import java.net.URI;

public final class S3UrlUtil {

    private S3UrlUtil() {
    }

    public static String extractStorageKey(String rawAvatar, String baseUrl) {
        if (rawAvatar == null || rawAvatar.isBlank()) {
            return rawAvatar;
        }

        String normalized = rawAvatar.trim();

        if (baseUrl != null && !baseUrl.isBlank()) {
            String baseWithSlash = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            String baseNoSlash = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

            while (normalized.startsWith(baseWithSlash) || normalized.startsWith(baseNoSlash)) {
                if (normalized.startsWith(baseWithSlash)) {
                    normalized = normalized.substring(baseWithSlash.length());
                } else {
                    normalized = normalized.substring(baseNoSlash.length());
                }
            }
        }

        try {
            if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                URI uri = URI.create(normalized);
                if (uri.getPath() != null && !uri.getPath().isBlank()) {
                    normalized = uri.getPath();
                }
            }
        } catch (Exception ignored) {
            // Keep best effort and continue normalization.
        }

        normalized = normalized.replaceFirst("^/+", "");

        int queryIdx = normalized.indexOf('?');
        if (queryIdx >= 0) {
            normalized = normalized.substring(0, queryIdx);
        }

        int fragmentIdx = normalized.indexOf('#');
        if (fragmentIdx >= 0) {
            normalized = normalized.substring(0, fragmentIdx);
        }

        return normalized;
    }
}