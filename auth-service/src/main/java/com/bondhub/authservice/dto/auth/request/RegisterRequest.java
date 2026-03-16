package com.bondhub.authservice.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration request DTO
 */
public record RegisterRequest(
        @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email,

        @NotBlank(message = "{validation.password.required}") @Size(min = 8, message = "{validation.password.size}") String password,

        String phoneNumber) {
}
