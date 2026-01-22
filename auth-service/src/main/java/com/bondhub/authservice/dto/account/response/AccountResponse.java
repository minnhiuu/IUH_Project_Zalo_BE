package com.bondhub.authservice.dto.account.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for account responses.
 * <p>
 * This record is used to send account information to clients.
 * Note that the password field is excluded for security reasons.
 * </p>
 *
 * @param id the unique identifier of the account
 * @param phoneNumber the phone number of the account
 * @param email the email address of the account
 * @param createdAt the timestamp when the account was created
 * @param lastModifiedAt the timestamp when the account was last modified
 * @param createdBy the user who created the account
 * @param lastModifiedBy the user who last modified the account
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountResponse(
        String id,
        String phoneNumber,
        String email,
        String role,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt,
        String createdBy,
        String lastModifiedBy
) {
}

