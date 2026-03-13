package com.bondhub.searchservice.dto.response;

import com.bondhub.searchservice.enums.ReindexTaskStatus;
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
