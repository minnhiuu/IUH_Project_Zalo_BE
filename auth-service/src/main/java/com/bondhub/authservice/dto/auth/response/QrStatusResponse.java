package com.bondhub.authservice.dto.auth.response;

import com.bondhub.authservice.enums.QrSessionStatus;
import lombok.Builder;

@Builder
public record QrStatusResponse(
        QrSessionStatus status,
        String userAvatar,
        String userFullName,
        String accessToken,
        String refreshToken
) {}
