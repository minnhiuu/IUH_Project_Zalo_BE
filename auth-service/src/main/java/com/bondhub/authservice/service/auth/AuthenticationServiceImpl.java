package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.client.UserServiceClient;
import com.bondhub.authservice.dto.auth.request.ForgotPasswordRequest;
import com.bondhub.authservice.dto.auth.request.LoginRequest;
import com.bondhub.authservice.dto.auth.request.RefreshRequest;
import com.bondhub.authservice.dto.auth.request.RegisterInitRequest;
import com.bondhub.authservice.dto.auth.request.RegisterRequest;
import com.bondhub.authservice.dto.auth.request.RegisterVerifyRequest;
import com.bondhub.authservice.dto.auth.request.ResetPasswordRequest;
import com.bondhub.authservice.dto.auth.response.ForgotPasswordResponse;
import com.bondhub.authservice.dto.auth.response.RegisterInitResponse;
import com.bondhub.authservice.dto.auth.response.TokenResponse;
import com.bondhub.authservice.enums.OtpPurpose;
import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.model.Account;
import com.bondhub.common.dto.client.userservice.user.request.UserCreateRequest;
import com.bondhub.common.event.account.AccountRegisteredEvent;
import com.bondhub.common.event.user.UserIndexEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.authservice.model.redis.PendingRegistration;
import com.bondhub.authservice.repository.AccountRepository;
import com.bondhub.authservice.repository.redis.PendingRegistrationRepository;
import com.bondhub.authservice.service.mail.MailService;
import com.bondhub.authservice.service.otp.OtpService;
import com.bondhub.authservice.service.token.TokenStoreService;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.enums.Role;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.JwtUtil;
import com.bondhub.authservice.util.TokenProvider;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    AccountRepository accountRepository;
    PendingRegistrationRepository pendingRegistrationRepository;
    PasswordEncoder passwordEncoder;
    JwtUtil jwtUtil;
    TokenStoreService tokenStoreService;
    SecurityUtil securityUtil;
    OtpService otpService;
    MailService mailService;
    UserServiceClient userServiceClient;
    MessageSource messageSource;
    TokenProvider tokenProvider;
    com.bondhub.common.publisher.OutboxEventPublisher outboxEventPublisher;

    @Override
    public TokenResponse login(LoginRequest request, String userAgent, String ipAddress) {
        log.info("Login attempt for email: {}, deviceId: {}, type: {}",
                request.email(), request.deviceId(), request.deviceType());

        Account account = accountRepository.findByEmail(request.email())
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            log.warn("Invalid password for email: {}", request.email());
            throw new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!account.getEnabled()) {
            throw new AppException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        String userId = null;
        try {
            var response = userServiceClient.getUserSummaryByAccountId(account.getId());
            if (response != null && response.data() != null) {
                userId = response.data().id();
                log.info("Fetched user profile for accountId: {}, userId: {}", account.getId(), userId);
            }
        } catch (Exception e) {
            log.error("Failed to fetch user profile via API for accountId: {}", account.getId(), e);
        }

        return tokenProvider.generateFullTokenResponse(
                account, request.deviceId(), request.deviceType(), userAgent, ipAddress);
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

        Account account = Account.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .phoneNumber(request.phoneNumber())
                .role(Role.USER)
                .enabled(true)
                .build();

        account = accountRepository.save(account);

        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(account.getId(), null, account.getEmail(), account.getRole(),
                sessionId);

        return TokenResponse.of(accessToken, null, 0);
    }

    @Override
    public TokenResponse refresh(String refreshToken, RefreshRequest request, String userAgent, String ipAddress) {
        if (refreshToken == null || !jwtUtil.validateToken(refreshToken)) {
            throw new AppException(ErrorCode.JWT_INVALID_TOKEN);
        }

        String sessionId = jwtUtil.extractSessionId(refreshToken);
        String accountId = jwtUtil.extractAccountId(refreshToken);

        if (sessionId == null || accountId == null) {
            throw new AppException(ErrorCode.JWT_INVALID_TOKEN);
        }

        boolean isValid = tokenStoreService.validateRefreshSessionWithBinding(
                sessionId, refreshToken, request.deviceId(), userAgent, ipAddress);

        if (!isValid) {
            log.warn("Refresh failed: Session invalid or binding mismatch for sessionId: {}", sessionId);
            throw new AppException(ErrorCode.JWT_INVALID_TOKEN);
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND));

        if (!account.getEnabled()) {
            throw new AppException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        tokenStoreService.revokeRefreshSession(sessionId);

        DeviceType deviceType = tokenStoreService.findRefreshSession(sessionId)
                .map(s -> s.getDeviceType())
                .orElse(DeviceType.WEB);

        return tokenProvider.generateFullTokenResponse(
                account, request.deviceId(), deviceType, userAgent, ipAddress);
    }

    @Override
    public void logout(String refreshToken) {
        try {
            if (securityUtil.isAuthenticated()) {
                String jti = securityUtil.getCurrentJwtId();
                String userId = securityUtil.getCurrentAccountId();
                String email = securityUtil.getCurrentEmail();
                long ttl = securityUtil.getRemainingTtlSeconds();

                tokenStoreService.blacklistAccessToken(jti, userId, email, ttl, "Logout");
            }
        } catch (Exception e) {
            log.warn("Could not blacklist access token during logout: {}", e.getMessage());
        }

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

        String jti = jwtUtil.extractJti(token);
        if (jti != null && tokenStoreService.isAccessTokenBlacklisted(jti)) {
            log.warn("Blocked attempt to use blacklisted access token: jti={}", jti);
            return false;
        }

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

    @Override
    public RegisterInitResponse initiateRegistration(RegisterInitRequest request) {
        log.info("Initiating registration for email: {}", request.email());

        // Validate password and confirm password match
        if (!request.password().equals(request.confirmPassword())) {
            throw new AppException(ErrorCode.ACC_PASSWORD_MISMATCH);
        }

        if (accountRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.ACC_EMAIL_ALREADY_USED);
        }

        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            if (accountRepository.existsByPhoneNumber(request.phoneNumber())) {
                throw new AppException(ErrorCode.ACC_PHONE_NUMBER_ALREADY_USED);
            }
        }

        String otp = otpService.generateAndStoreOtp(request.email(), OtpPurpose.REGISTRATION);

        long now = System.currentTimeMillis();

        PendingRegistration pendingReg = PendingRegistration.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phoneNumber(request.phoneNumber())
                .createdAt(now)
                .ttl(300L)
                .build();

        pendingRegistrationRepository.save(pendingReg);

        mailService.sendOtpEmail(request.email(), otp, "Registration Verification");

        log.info("✅ Registration initiated successfully for: {}", request.email());

        String message = messageSource.getMessage(
                "auth.register.otp.sent",
                null,
                LocaleContextHolder.getLocale());
        return RegisterInitResponse.of(message, request.email());
    }

    @Override
    public TokenResponse verifyAndCompleteRegistration(
            RegisterVerifyRequest request, String userAgent, String ipAddress) {

        log.info("Verifying OTP and completing registration for: {}", request.email());

        boolean isValid = otpService.validateOtp(
                request.email(),
                request.otp(),
                OtpPurpose.REGISTRATION);

        if (!isValid) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        PendingRegistration pendingReg = pendingRegistrationRepository.findById(request.email())
                .orElseThrow(() -> new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND));

        Account account = Account.builder()
                .email(pendingReg.getEmail())
                .password(pendingReg.getPasswordHash())
                .phoneNumber(pendingReg.getPhoneNumber())
                .role(Role.USER)
                .isVerified(true)
                .enabled(true)
                .build();

        account = accountRepository.save(account);

        pendingRegistrationRepository.delete(pendingReg);

        log.info("✅ Account created and verified for: {}", account.getEmail());

        // Call user-service to create user profile synchronously
        String userId = null;
        try {
            var createRequest = UserCreateRequest.builder()
                    .accountId(account.getId())
                    .fullName(pendingReg.getFullName())
                    .build();

            var response = userServiceClient.createUser(createRequest);
            if (response != null && response.data() != null) {
                userId = response.data().id();
                log.info("✅ User profile created via API for accountId: {}, userId: {}", account.getId(), userId);
                
                UserIndexEvent indexEvent = UserIndexEvent.builder()
                        .userId(userId)
                        .phoneNumber(account.getPhoneNumber())
                        .role(account.getRole())
                        .build();

                outboxEventPublisher.saveAndPublish(
                        userId,
                        "User",
                        EventType.USER_INDEX,
                        indexEvent
                );
                log.info("📤 Published USER_INDEX event for userId: {}", userId);
            }
        } catch (Exception e) {
            log.error("❌ Failed to create user profile via API for accountId: {}", account.getId(), e);
        }

        return tokenProvider.generateFullTokenResponse(
                account,
                request.deviceId(),
                request.deviceType(),
                userAgent,
                ipAddress);
    }

    @Override
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        log.info("Initiating password reset for email: {}", request.email());

        if (!accountRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND);
        }

        String otp = otpService.generateAndStoreOtp(request.email(), OtpPurpose.PASSWORD_RESET);

        mailService.sendPasswordResetOtpEmail(request.email(), otp);

        log.info("✅ Password reset OTP sent to: {}", request.email());
        return ForgotPasswordResponse.of(request.email());
    }

    @Override
    public TokenResponse resetPassword(ResetPasswordRequest request, String userAgent, String ipAddress) {
        log.info("Resetting password for email: {}", request.email());

        boolean isValid = otpService.validateOtp(
                request.email(),
                request.otp(),
                OtpPurpose.PASSWORD_RESET);

        if (!isValid) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        Account account = accountRepository.findByEmail(request.email())
                .orElseThrow(() -> new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND));

        account.setPassword(passwordEncoder.encode(request.newPassword()));
        account = accountRepository.save(account);

        log.info("✅ Password successfully reset for: {}", account.getEmail());

        return tokenProvider.generateFullTokenResponse(
                account,
                "web-device", // Default or from request if available
                DeviceType.WEB,
                userAgent,
                ipAddress);
    }
}
