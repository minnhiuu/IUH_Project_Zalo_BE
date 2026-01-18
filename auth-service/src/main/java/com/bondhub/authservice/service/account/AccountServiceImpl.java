package com.bondhub.authservice.service.account;

import com.bondhub.authservice.dto.account.request.AccountCreateRequest;
import com.bondhub.authservice.dto.account.response.AccountResponse;
import com.bondhub.authservice.dto.account.request.AccountUpdateRequest;
import com.bondhub.authservice.mapper.AccountMapper;
import com.bondhub.authservice.model.Account;
import com.bondhub.authservice.repository.AccountRepository;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AccountServiceImpl implements AccountService {

    AccountRepository accountRepository;
    AccountMapper accountMapper;
    PasswordEncoder passwordEncoder;

    @Override
    public ApiResponse<AccountResponse> createAccount(AccountCreateRequest request) {
        log.info("Creating new account with email: {}", request.email());

        if (request.email() != null && accountRepository.existsByEmail(request.email())) {
            log.warn("Account with email {} already exists", request.email());
            throw new AppException(ErrorCode.ACC_EMAIL_ALREADY_USED);
        }

        if (request.phoneNumber() != null && accountRepository.existsByPhoneNumber(request.phoneNumber())) {
            log.warn("Account with phone number {} already exists", request.phoneNumber());
            throw new AppException(ErrorCode.ACC_PHONE_NUMBER_ALREADY_USED);
        }

        Account account = accountMapper.toEntity(request);
        account.setPassword(passwordEncoder.encode(request.password()));

        Account savedAccount = accountRepository.save(account);
        log.info("Account created successfully with id: {}", savedAccount.getId());
        return ApiResponse.success(accountMapper.toResponse(savedAccount));
    }

    @Override
    public ApiResponse<AccountResponse> getAccountById(String id) {
        log.info("Fetching account with id: {}", id);
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Account not found with id: {}", id);
                    return new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND);
                });

        log.info("Account found with id: {}", id);
        return ApiResponse.success(accountMapper.toResponse(account));
    }

    @Override
    public ApiResponse<AccountResponse> getAccountByEmail(String email) {
        log.info("Fetching account with email: {}", email);
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Account not found with email: {}", email);
                    return new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND);
                });

        log.info("Account found with email: {}", email);
        return ApiResponse.success(accountMapper.toResponse(account));
    }

    @Override
    public ApiResponse<AccountResponse> getAccountByPhoneNumber(String phoneNumber) {
        log.info("Fetching account with phone number: {}", phoneNumber);
        Account account = accountRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found with phone number: {}", phoneNumber);
                    return new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND);
                });

        log.info("Account found with phone number: {}", phoneNumber);
        return ApiResponse.success(accountMapper.toResponse(account));
    }

    @Override
    public ApiResponse<List<AccountResponse>> getAllAccounts() {
        log.info("Fetching all accounts");
        List<Account> accounts = accountRepository.findAll();
        log.info("Found {} accounts", accounts.size());
        List<AccountResponse> accountResponses = accounts.stream()
                .map(accountMapper::toResponse)
                .toList();
        return ApiResponse.success(accountResponses);
    }

    @Override
    public ApiResponse<AccountResponse> updateAccount(String id, AccountUpdateRequest request) {
        log.info("Updating account with id: {}", id);
        Account accountToUpdate = accountRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Account not found with id: {}", id);
                    return new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND);
                });

        if (request.email() != null && !request.email().equals(accountToUpdate.getEmail())) {
            if (accountRepository.existsByEmail(request.email())) {
                log.warn("Email {} already exists for another account", request.email());
                throw new AppException(ErrorCode.ACC_EMAIL_ALREADY_USED);
            }
        }

        if (request.phoneNumber() != null && !request.phoneNumber().equals(accountToUpdate.getPhoneNumber())) {
            if (accountRepository.existsByPhoneNumber(request.phoneNumber())) {
                log.warn("Phone number {} already exists for another account", request.phoneNumber());
                throw new AppException(ErrorCode.ACC_PHONE_NUMBER_ALREADY_USED);
            }
        }

        accountMapper.updateEntityFromRequest(accountToUpdate, request);

        if (request.password() != null) {
            accountToUpdate.setPassword(passwordEncoder.encode(request.password()));
        }

        Account updatedAccount = accountRepository.save(accountToUpdate);
        log.info("Account updated successfully with id: {}", id);
        return ApiResponse.success(accountMapper.toResponse(updatedAccount));
    }

    @Override
    public ApiResponse<Void> deleteAccount(String id) {
        log.info("Deleting account with id: {}", id);

        if (!accountRepository.existsById(id)) {
            log.warn("Account not found with id: {}", id);
            throw new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND);
        }

        accountRepository.deleteById(id);
        log.info("Account deleted successfully with id: {}", id);
        return ApiResponse.successWithoutData();
    }

    @Override
    public ApiResponse<Boolean> existsByEmail(String email) {
        log.info("Checking if account exists with email: {}", email);
        boolean exists = accountRepository.existsByEmail(email);
        log.info("Account with email {} exists: {}", email, exists);
        return ApiResponse.success(exists);
    }

    @Override
    public ApiResponse<Boolean> existsByPhoneNumber(String phoneNumber) {
        log.info("Checking if account exists with phone number: {}", phoneNumber);
        boolean exists = accountRepository.existsByPhoneNumber(phoneNumber);
        log.info("Account with phone number {} exists: {}", phoneNumber, exists);
        return ApiResponse.success(exists);
    }
}
