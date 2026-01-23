package com.bondhub.authservice.dto.auth.request;

import com.bondhub.authservice.enums.DeviceType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for verifying OTP and completing registration (Step 2)
 */
public record RegisterVerifyRequest(
        @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}")
        String email,

        @NotBlank(message = "{validation.otp.required}")
        @Pattern(regexp = "^[0-9]{6}$", message = "{validation.otp.pattern}")
        String otp,

        @NotBlank(message = "{validation.deviceId.required}")
        String deviceId,

        @NotNull(message = "{validation.deviceType.required}")
        DeviceType deviceType
) {
}
