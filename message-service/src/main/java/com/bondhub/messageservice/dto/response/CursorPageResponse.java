package com.bondhub.messageservice.dto.response;

import lombok.Builder;
import java.util.List;

@Builder
public record CursorPageResponse<T>(
    List<T> data,
    String olderCursor,
    String newerCursor,
    Boolean hasMoreOlder,
    Boolean hasMoreNewer,
    Boolean isJumpResult
) {}
