package com.bondhub.authservice.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
                @NotBlank(message = "{validation.oldPassword.required}") String oldPassword,

                @NotBlank(message = "{validation.newPassword.required}") @Size(min = 8, message = "{validation.password.size}") String newPassword,

                Boolean logoutOtherDevices) {
}
