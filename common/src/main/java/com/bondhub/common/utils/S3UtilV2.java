package com.bondhub.common.utils;

import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class S3UtilV2 {

    @Value("${aws.s3.bucket.name:bondhub-bucket}")
    String bucketName;

    @Value("${cloud.aws.region.static:ap-southeast-1}")
    String region;

    public String getS3BaseUrl() {
        return String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
    }

    public String getS3BaseUrl(String customBucket, String customRegion) {
        return String.format("https://%s.s3.%s.amazonaws.com/", customBucket, customRegion);
    }

    public String getFullUrl(String originalAvatar) {
        if (originalAvatar == null || originalAvatar.isBlank()) {
            return null;
        }
        String baseUrl = getS3BaseUrl();
        return baseUrl + extractStorageKey(originalAvatar, baseUrl);
    }

    public String extractStorageKey(String rawAvatar) {
        return extractStorageKey(rawAvatar, getS3BaseUrl());
    }

    public String extractStorageKey(String rawAvatar, String baseUrl) {
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

    public String getBucketName() {
        return bucketName;
    }

    public String getRegion() {
        return region;
    }
}
