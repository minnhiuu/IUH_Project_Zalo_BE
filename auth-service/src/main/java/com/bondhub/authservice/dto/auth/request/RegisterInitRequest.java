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
                @NotBlank(message = "Email is required") @Email(message = "Email must be valid") String email,

                @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must be at least 8 characters") String password,

                @NotBlank(message = "Full name is required") String fullName,

                @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone number must be 10-15 digits") String phoneNumber) {
}
