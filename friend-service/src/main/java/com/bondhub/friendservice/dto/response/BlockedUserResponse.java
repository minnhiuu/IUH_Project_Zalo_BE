package com.bondhub.friendservice.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Response containing blocked user information
 */
@Builder
public record BlockedUserResponse(
    String id,
    String blockerId,
    String blockedUserId,
    BlockPreferenceResponse preference,
    LocalDateTime createdAt,
    LocalDateTime lastModifiedAt
) {}
