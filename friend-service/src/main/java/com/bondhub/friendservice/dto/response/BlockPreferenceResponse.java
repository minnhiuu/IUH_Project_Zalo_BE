package com.bondhub.friendservice.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Response containing block preference information
 */
@Builder
public record BlockPreferenceResponse(
    boolean message,
    boolean call,
    boolean story
) {}
