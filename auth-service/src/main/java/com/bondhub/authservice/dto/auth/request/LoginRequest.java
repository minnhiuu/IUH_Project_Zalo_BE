package com.bondhub.authservice.dto.auth.request;

import com.bondhub.authservice.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Login request DTO
 */
public record LoginRequest(
        @NotBlank(message = "{validation.email.required}") @jakarta.validation.constraints.Email(message = "{validation.email.invalid}") String email,

        @NotBlank(message = "{validation.password.required}") String password,

        @NotBlank(message = "{validation.deviceId.required}") String deviceId,

        @NotNull(message = "{validation.deviceType.required}") DeviceType deviceType) {
}
