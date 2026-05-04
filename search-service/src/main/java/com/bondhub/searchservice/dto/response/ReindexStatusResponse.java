package com.bondhub.searchservice.dto.response;

import com.bondhub.searchservice.enums.ReindexTaskStatus;
import com.bondhub.searchservice.enums.SearchIndexType;
import lombok.Builder;

@Builder
public record ReindexStatusResponse(
    String taskId,
    SearchIndexType type,
    ReindexTaskStatus status,
    long total,
    long processed,
    int percentage,
    String message
) {
}
