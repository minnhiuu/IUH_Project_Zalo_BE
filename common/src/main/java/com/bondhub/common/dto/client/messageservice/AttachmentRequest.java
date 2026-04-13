package com.bondhub.common.dto.client.messageservice;

public record AttachmentRequest(
        String key,
        String url,
        String fileName,
        String originalFileName,
        String contentType,
        Long size
) {}
