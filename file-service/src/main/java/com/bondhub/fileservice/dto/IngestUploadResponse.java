package com.bondhub.fileservice.dto;

public record IngestUploadResponse(
        String docId,
        String conversationId,
        String key,
        String fileName,
        String originalFileName,
        String contentType,
        Long size
) {
}
