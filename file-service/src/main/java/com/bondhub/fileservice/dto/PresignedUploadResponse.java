package com.bondhub.fileservice.dto;

import lombok.Builder;
import java.time.Instant;

@Builder
public record PresignedUploadResponse(
    String key,
    String presignedUrl,
    String publicUrl,
    String contentType,
    String originalFileName,
    Long size,
    Instant expiresAt
) {}
