package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.dto.auth.request.LoginRequest;
import com.bondhub.authservice.dto.auth.request.RefreshTokenRequest;
import com.bondhub.authservice.dto.auth.request.RegisterRequest;
import com.bondhub.authservice.dto.auth.response.TokenResponse;
import com.bondhub.authservice.model.Account;
import com.bondhub.authservice.repository.AccountRepository;
import com.bondhub.common.config.JwtProperties;
import com.bondhub.common.enums.Role;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Authentication service implementation
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    AccountRepository accountRepository;
    PasswordEncoder passwordEncoder;
    JwtUtil jwtUtil;
    JwtProperties jwtProperties;

    @Override
    public TokenResponse login(LoginRequest request) {
        log.info("Login attempt for phone number: {}", request.phoneNumber());

        Account account = accountRepository.findByPhoneNumber(request.phoneNumber())
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            log.warn("Invalid password for phone number: {}", request.phoneNumber());
            throw new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!account.getEnabled()) {
            log.warn("Account disabled for phone number: {}", request.phoneNumber());
            throw new AppException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        String accessToken = jwtUtil.generateAccessToken(
                account.getId(),
                account.getEmail(),
                account.getRoles() != null ? account.getRoles() : new HashSet<>());
        String refreshToken = jwtUtil.generateRefreshToken(account.getId());

        log.info("Login successful for phone number: {}", request.phoneNumber());

        return TokenResponse.of(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenExpiration());
    }

    @Override
    public TokenResponse register(RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.email());

        if (accountRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.ACC_EMAIL_ALREADY_USED);
        }

        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            if (accountRepository.existsByPhoneNumber(request.phoneNumber())) {
                throw new AppException(ErrorCode.ACC_PHONE_NUMBER_ALREADY_USED);
            }
        }

        Set<Role> defaultRoles = new HashSet<>();
        defaultRoles.add(Role.USER);

        Account account = Account.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .phoneNumber(request.phoneNumber())
                .roles(defaultRoles)
                .enabled(true)
                .build();

        account = accountRepository.save(account);

        String accessToken = jwtUtil.generateAccessToken(
                account.getId(),
                account.getEmail(),
                account.getRoles());
        String refreshToken = jwtUtil.generateRefreshToken(account.getId());

        log.info("Registration successful for email: {}", request.email());

        return TokenResponse.of(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenExpiration());
    }

    @Override
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtUtil.validateToken(refreshToken)) {
            throw new AppException(ErrorCode.JWT_INVALID_TOKEN);
        }

        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new AppException(ErrorCode.JWT_INVALID_TOKEN);
        }

        String userId = jwtUtil.extractUserId(refreshToken);

        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND));

        if (!account.getEnabled()) {
            throw new AppException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        String newAccessToken = jwtUtil.generateAccessToken(
                account.getId(),
                account.getEmail(),
                account.getRoles() != null ? account.getRoles() : new HashSet<>());
        String newRefreshToken = jwtUtil.generateRefreshToken(account.getId());

        log.info("Token refresh successful for user: {}", userId);

        return TokenResponse.of(
                newAccessToken,
                newRefreshToken,
                jwtProperties.getAccessTokenExpiration());
    }

    @Override
    public void logout(com.bondhub.authservice.dto.auth.request.LogoutRequest request) {
        String refreshToken = request.refreshToken();
        log.info("Logout request received with refresh token");

        if (jwtUtil.validateToken(refreshToken) && jwtUtil.isRefreshToken(refreshToken)) {
            String userId = jwtUtil.extractUserId(refreshToken);
            log.info("User {} logged out successfully", userId);
        } else {
            log.warn("Logout attempt with invalid or non-refresh token");
        }

        // TODO: Add redis logic
    }

    @Override
    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }
}
