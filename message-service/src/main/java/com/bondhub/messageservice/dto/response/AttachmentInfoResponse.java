package com.bondhub.messageservice.dto.response;

import lombok.Builder;

@Builder
public record AttachmentInfoResponse(
        String key,
        String url,
        String fileName,
        String originalFileName,
        String contentType,
        Long size
) {}
