package com.bondhub.authservice.service.account;

import com.bondhub.authservice.dto.account.request.AccountCreateRequest;
import com.bondhub.authservice.dto.account.response.AccountResponse;
import com.bondhub.authservice.dto.account.request.AccountUpdateRequest;
import com.bondhub.common.dto.ApiResponse;

import java.util.List;

/**
 * Service interface for managing Account operations.
 * <p>
 * This service provides CRUD operations for Account entities using DTOs
 * and additional utility methods for checking account existence by email and phone number.
 * All methods return {@link ApiResponse} objects for consistent response handling.
 * </p>
 *
 * @author BondHub Development Team
 * @version 1.0
 * @since 2026-01-15
 */
public interface AccountService {

    /**
     * Creates a new account in the system.
     * <p>
     * Validates that the email and phone number are unique before creating the account.
     * </p>
     *
     * @param request the account creation request DTO, must not be null
     * @return the created account response DTO with generated ID
     * @throws AppException if email already exists (ACC_EMAIL_ALREADY_USED)
     *                      or if phone number already exists (ACC_PHONE_NUMBER_ALREADY_USED)
     */
    AccountResponse createAccount(AccountCreateRequest request);

    /**
     * Retrieves an account by its unique identifier.
     *
     * @param id the unique identifier of the account, must not be null
     * @return the account response DTO if found
     * @throws AppException if account not found (ACC_ACCOUNT_NOT_FOUND)
     */
    AccountResponse getAccountById(String id);

    /**
     * Retrieves an account by email address.
     *
     * @param email the email address to search for, must not be null
     * @return the account response DTO if found
     * @throws AppException if account not found (ACC_ACCOUNT_NOT_FOUND)
     */
    AccountResponse getAccountByEmail(String email);

    /**
     * Retrieves an account by phone number.
     *
     * @param phoneNumber the phone number to search for, must not be null
     * @return the account response DTO if found
     * @throws AppException if account not found (ACC_ACCOUNT_NOT_FOUND)
     */
    AccountResponse getAccountByPhoneNumber(String phoneNumber);

    /**
     * Retrieves all accounts in the system.
     *
     * @return a list of all account response DTOs (may be empty)
     */
    List<AccountResponse> getAllAccounts();

    /**
     * Updates an existing account with new information.
     * <p>
     * Only the provided fields will be updated. If email or phone number is changed,
     * validates that the new value is unique before updating.
     * </p>
     *
     * @param id the unique identifier of the account to update, must not be null
     * @param request the account update request DTO containing updated information
     * @return the updated account response DTO
     * @throws AppException if account not found (ACC_ACCOUNT_NOT_FOUND),
     *                      email already exists (ACC_EMAIL_ALREADY_USED),
     *                      or phone number already exists (ACC_PHONE_NUMBER_ALREADY_USED)
     */
    AccountResponse updateAccount(String id, AccountUpdateRequest request);

    /**
     * Deletes an account from the system.
     *
     * @param id the unique identifier of the account to delete, must not be null
     * @throws AppException if account not found (ACC_ACCOUNT_NOT_FOUND)
     */
    void deleteAccount(String id);

    /**
     * Checks if an account with the specified email exists.
     *
     * @param email the email address to check, must not be null
     * @return true if account exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Checks if an account with the specified phone number exists.
     *
     * @param phoneNumber the phone number to check, must not be null
     * @return true if account exists, false otherwise
     */
    boolean existsByPhoneNumber(String phoneNumber);
}

