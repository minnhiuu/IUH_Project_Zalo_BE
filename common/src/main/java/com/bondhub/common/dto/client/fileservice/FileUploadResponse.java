package com.bondhub.common.dto.client.fileservice;

import lombok.Builder;

@Builder
public record FileUploadResponse(
    String key,
    String url,
    String fileName,
    String originalFileName,
    String contentType,
    Long size
) {}
