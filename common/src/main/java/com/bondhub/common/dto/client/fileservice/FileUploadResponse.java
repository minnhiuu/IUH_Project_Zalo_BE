package com.bondhub.common.dto.client.fileservice;

import lombok.Builder;

@Builder
public record FileUploadResponse(
    String fileName,
    String key
) {}
