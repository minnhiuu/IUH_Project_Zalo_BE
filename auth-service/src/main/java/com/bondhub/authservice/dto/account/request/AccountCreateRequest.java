package com.bondhub.authservice.dto.account.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for creating a new account.
 * <p>
 * This record is used to receive account creation requests from clients.
 * All fields are validated to ensure data integrity.
 * </p>
 *
 * @param email       the email address of the account, must be valid and not
 *                    blank
 * @param password    the password for the account, must not be blank
 * @param phoneNumber the phone number of the account, must not be blank
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountCreateRequest(
                @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email,

                @NotBlank(message = "{validation.password.required}") String password,

                @NotBlank(message = "{validation.phoneNumber.required}") String phoneNumber) {
}
