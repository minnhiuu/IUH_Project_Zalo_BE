package com.bondhub.authservice.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
                @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email,

                @NotBlank(message = "{validation.otp.required}") @Pattern(regexp = "^[0-9]{6}$", message = "{validation.otp.pattern}") String otp,

                @NotBlank(message = "{validation.newPassword.required}") @Size(min = 8, message = "{validation.password.size}") String newPassword,

                Boolean logoutOtherDevices) {
}
