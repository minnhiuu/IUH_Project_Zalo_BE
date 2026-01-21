package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.dto.auth.request.LoginRequest;
import com.bondhub.authservice.dto.auth.request.RefreshRequest;
import com.bondhub.authservice.dto.auth.request.RegisterRequest;
import com.bondhub.authservice.dto.auth.response.TokenResponse;
import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.model.Account;
import com.bondhub.authservice.repository.AccountRepository;
import com.bondhub.authservice.service.token.TokenStoreService;
import com.bondhub.authservice.util.SecurityUtil;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    AccountRepository accountRepository;
    PasswordEncoder passwordEncoder;
    JwtUtil jwtUtil;
    JwtProperties jwtProperties;
    TokenStoreService tokenStoreService;
    SecurityUtil securityUtil;

    @Override
    public TokenResponse login(LoginRequest request, String userAgent, String ipAddress) {
        log.info("Login attempt for phone number: {}, deviceId: {}, type: {}",
                request.phoneNumber(), request.deviceId(), request.deviceType());

        Account account = accountRepository.findByPhoneNumber(request.phoneNumber())
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            log.warn("Invalid password for phone number: {}", request.phoneNumber());
            throw new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!account.getEnabled()) {
            throw new AppException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        return generateFullTokenResponse(account, request.deviceId(), request.deviceType(), userAgent, ipAddress);
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

        // For simplicity during registration, I don't handle device info yet.
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(account.getId(), account.getEmail(), account.getRoles(),
                sessionId);

        return TokenResponse.of(accessToken, null, jwtProperties.getAccessTokenExpiration());
    }

    @Override
    public TokenResponse refresh(String refreshToken, RefreshRequest request, String userAgent, String ipAddress) {
        if (refreshToken == null || !jwtUtil.validateToken(refreshToken)) {
            throw new AppException(ErrorCode.JWT_INVALID_TOKEN);
        }

        String sessionId = jwtUtil.extractSessionId(refreshToken);
        String userId = jwtUtil.extractUserId(refreshToken);

        if (sessionId == null || userId == null) {
            throw new AppException(ErrorCode.JWT_INVALID_TOKEN);
        }

        // Validate session in Redis with device binding
        boolean isValid = tokenStoreService.validateRefreshSessionWithBinding(
                sessionId, refreshToken, request.deviceId(), userAgent, ipAddress);

        if (!isValid) {
            log.warn("Refresh failed: Session invalid or binding mismatch for sessionId: {}", sessionId);
            throw new AppException(ErrorCode.JWT_INVALID_TOKEN);
        }

        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND));

        if (!account.getEnabled()) {
            throw new AppException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        // Revoke old session (Rotation)
        tokenStoreService.revokeRefreshSession(sessionId);

        // Detect device type from existing session or logic (here we assume it persists
        // or we could extract from session)
        DeviceType deviceType = tokenStoreService.findRefreshSession(sessionId)
                .map(s -> s.getDeviceType())
                .orElse(DeviceType.WEB);

        // Issue new tokens
        return generateFullTokenResponse(account, request.deviceId(), deviceType, userAgent, ipAddress);
    }

    @Override
    public void logout(String refreshToken) {
        // 1. Blacklist current Access Token (using JTI from SecurityContext)
        try {
            if (securityUtil.isAuthenticated()) {
                String jti = securityUtil.getCurrentJwtId();
                String userId = securityUtil.getCurrentUserId();
                String email = securityUtil.getCurrentEmail();
                long ttl = securityUtil.getRemainingTtlSeconds();

                tokenStoreService.blacklistAccessToken(jti, userId, email, ttl, "Logout");
            }
        } catch (Exception e) {
            log.warn("Could not blacklist access token during logout: {}", e.getMessage());
        }

        // 2. Revoke Refresh Token Session in Redis
        if (refreshToken != null && jwtUtil.validateToken(refreshToken)) {
            String sessionId = jwtUtil.extractSessionId(refreshToken);
            if (sessionId != null) {
                tokenStoreService.revokeRefreshSession(sessionId);
            }
        }

        log.info("Logout processed");
    }

    @Override
    public boolean validateToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            return false;
        }

        // 1. Check JTI Blacklist (for explicitly logged out tokens)
        String jti = jwtUtil.extractJti(token);
        if (jti != null && tokenStoreService.isAccessTokenBlacklisted(jti)) {
            log.warn("Blocked attempt to use blacklisted access token: jti={}", jti);
            return false;
        }

        // 2. Check if Session still exists in Redis (for kicked/revoked sessions)
        String sessionId = jwtUtil.extractSessionId(token);
        if (sessionId != null) {
            boolean sessionExists = tokenStoreService.findRefreshSession(sessionId)
                    .map(session -> !Boolean.TRUE.equals(session.getRevoked()))
                    .orElse(false);

            if (!sessionExists) {
                log.warn("Access token rejected: Session no longer exists or is revoked: sessionId={}", sessionId);
                return false;
            }
        }

        return true;
    }

    private TokenResponse generateFullTokenResponse(Account account, String deviceId, DeviceType deviceType,
            String userAgent, String ipAddress) {
        String sessionId = UUID.randomUUID().toString();

        long refreshExpirationMs = (deviceType == DeviceType.MOBILE)
                ? jwtUtil.getMobileRefreshExpirationMs()
                : jwtUtil.getWebRefreshExpirationMs();

        String accessToken = jwtUtil.generateAccessToken(account.getId(), account.getEmail(), account.getRoles(),
                sessionId);
        String refreshToken = jwtUtil.generateRefreshToken(account.getId(), sessionId, refreshExpirationMs);

        tokenStoreService.createRefreshSession(
                sessionId,
                account.getId(),
                account.getPhoneNumber(),
                deviceId,
                deviceType,
                refreshToken,
                userAgent,
                ipAddress,
                refreshExpirationMs / 1000);

        return TokenResponse.of(accessToken, refreshToken, refreshExpirationMs / 1000);
    }
}
