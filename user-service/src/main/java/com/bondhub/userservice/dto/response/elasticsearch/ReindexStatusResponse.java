package com.bondhub.userservice.dto.response.elasticsearch;

import com.bondhub.userservice.enums.ReindexTaskStatus;
import lombok.Builder;

@Builder
public record ReindexStatusResponse(
    String taskId,
    ReindexTaskStatus status,
    long total,
    long processed,
    int percentage,
    String message
) {}
