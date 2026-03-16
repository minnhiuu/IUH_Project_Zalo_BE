package com.bondhub.userservice.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Response containing audit information
 */
@Builder
public record AuditResponse(
    LocalDateTime createdAt,
    LocalDateTime lastModifiedAt,
    String createdBy,
    String lastModifiedBy,
    LocalDateTime lastLoginAt,
    boolean active
) {}
