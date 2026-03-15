package com.bondhub.authservice.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for initiating user registration (Step 1)
 * Server will validate, generate OTP, and send email
 */
public record RegisterInitRequest(
                @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email,

                @NotBlank(message = "{validation.password.required}") @Size(min = 8, message = "{validation.password.size}") String password,

                @NotBlank(message = "{validation.confirmPassword.required}") String confirmPassword,

                @NotBlank(message = "{validation.fullName.required}") String fullName,

                @Pattern(regexp = "^[0-9]{10,15}$", message = "{validation.phoneNumber.pattern}") String phoneNumber) {
}
