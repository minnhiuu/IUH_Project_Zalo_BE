package com.bondhub.notificationservices.dto.request.user.device;

import com.bondhub.notificationservices.enums.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeviceTokenRequest(
    @NotBlank(message = "validation.fcm.token.required")
    String token,

    @NotNull(message = "validation.device.platform.required")
    Platform platform,

    String deviceId,

    String locale
) {}
