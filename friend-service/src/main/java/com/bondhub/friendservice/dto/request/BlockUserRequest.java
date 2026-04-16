package com.bondhub.friendservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Request to block a user
 */
@Builder
public record BlockUserRequest(
    @NotBlank(message = "Blocked user ID is required")
    String blockedUserId,

    Boolean blockMessage,  // Optional: defaults to true if not provided
    Boolean blockCall,     // Optional: defaults to true if not provided
    Boolean blockStory     // Optional: defaults to true if not provided
) {}
