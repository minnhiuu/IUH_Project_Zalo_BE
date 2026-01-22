package com.bondhub.authservice.controller;

import com.bondhub.authservice.dto.account.request.AccountCreateRequest;
import com.bondhub.authservice.dto.account.response.AccountResponse;
import com.bondhub.authservice.dto.account.request.AccountUpdateRequest;
import com.bondhub.authservice.service.account.AccountService;
import com.bondhub.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth/accounts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AccountController {

    AccountService accountService;

    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(@Valid @RequestBody AccountCreateRequest request) {
        log.info("REST request to create account with email: {}", request.email());
        AccountResponse accountResponse = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(accountResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(@PathVariable String id) {
        log.info("REST request to get account by id: {}", id);
        AccountResponse accountResponse = accountService.getAccountById(id);
        return ResponseEntity.ok(ApiResponse.success(accountResponse));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountByEmail(@PathVariable String email) {
        log.info("REST request to get account by email: {}", email);
        AccountResponse accountResponse = accountService.getAccountByEmail(email);
        return ResponseEntity.ok(ApiResponse.success(accountResponse));
    }

    @GetMapping("/phone/{phoneNumber}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountByPhoneNumber(@PathVariable String phoneNumber) {
        log.info("REST request to get account by phone number: {}", phoneNumber);
        AccountResponse accountResponse = accountService.getAccountByPhoneNumber(phoneNumber);
        return ResponseEntity.ok(ApiResponse.success(accountResponse));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAllAccounts() {
        log.info("REST request to get all accounts");
        List<AccountResponse> accountResponses = accountService.getAllAccounts();
        return ResponseEntity.ok(ApiResponse.success(accountResponses));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @PathVariable String id,
            @Valid @RequestBody AccountUpdateRequest request) {
        log.info("REST request to update account with id: {}", id);
        AccountResponse accountResponse = accountService.updateAccount(id, request);
        return ResponseEntity.ok(ApiResponse.success(accountResponse));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable String id) {
        log.info("REST request to delete account with id: {}", id);
        accountService.deleteAccount(id);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @GetMapping("/exists/email/{email}")
    public ResponseEntity<ApiResponse<Boolean>> existsByEmail(@PathVariable String email) {
        log.info("REST request to check if account exists by email: {}", email);
        boolean exists = accountService.existsByEmail(email);
        return ResponseEntity.ok(ApiResponse.success(exists));
    }

    @GetMapping("/exists/phone/{phoneNumber}")
    public ResponseEntity<ApiResponse<Boolean>> existsByPhoneNumber(@PathVariable String phoneNumber) {
        log.info("REST request to check if account exists by phone number: {}", phoneNumber);
        boolean exists = accountService.existsByPhoneNumber(phoneNumber);
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
}
