package com.bondhub.messageservice.dto.response;

import lombok.Builder;
import lombok.With;

@Builder(toBuilder = true)
@With
public record AttachmentInfoResponse(
        String key,
        String url,
        String fileName,
        String originalFileName,
        String contentType,
        Long size
) {}
