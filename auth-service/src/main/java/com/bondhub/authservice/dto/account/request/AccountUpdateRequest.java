package com.bondhub.authservice.dto.account.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;

/**
 * Data Transfer Object for updating an existing account.
 * <p>
 * This record is used to receive account update requests from clients.
 * All fields are optional - only provided fields will be updated.
 * </p>
 *
 * @param phoneNumber the new phone number (optional)
 * @param password the new password (optional)
 * @param email the new email address (optional, must be valid if provided)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountUpdateRequest(
        String phoneNumber,
        String password,

        @Email(message = "{validation.email.invalid}")
        String email
) {
}

