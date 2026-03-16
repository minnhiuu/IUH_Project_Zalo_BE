package com.bondhub.userservice.model;

import lombok.Builder;

/**
 * Structured metadata for user activity log entries.
 * Fields are action-specific and may be null when not applicable.
 */
@Builder
public record ActivityLogMetadata(
        String reason,
        String accountId
) {}
