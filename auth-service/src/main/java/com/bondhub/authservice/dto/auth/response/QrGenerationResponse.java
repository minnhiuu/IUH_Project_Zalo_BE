package com.bondhub.authservice.dto.auth.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record QrGenerationResponse(
        String qrId,
        String qrContent,
        LocalDateTime expiresAt
) {}