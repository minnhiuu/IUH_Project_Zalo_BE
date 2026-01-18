package com.bondhub.authservice.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Login request DTO
 */
public record LoginRequest(
                @NotBlank(message = "Phone number is required") @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format") String phoneNumber,
                @NotBlank(message = "Password is required") String password) {
}
