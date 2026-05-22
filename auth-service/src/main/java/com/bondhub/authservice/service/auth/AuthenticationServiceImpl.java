package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.client.UserServiceClient;
import com.bondhub.authservice.dto.auth.request.*;
import com.bondhub.authservice.dto.auth.response.ForgotPasswordResponse;
import com.bondhub.authservice.dto.auth.response.RegisterInitResponse;
import com.bondhub.authservice.dto.auth.response.TokenResponse;
import com.bondhub.authservice.dto.device.request.DeviceUpdateRequest;
import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.enums.OtpPurpose;
import com.bondhub.authservice.model.Account;
import com.bondhub.authservice.model.redis.PendingRegistration;
import com.bondhub.authservice.model.redis.RefreshTokenSession;
import com.bondhub.authservice.repository.AccountRepository;
import com.bondhub.authservice.repository.redis.PendingRegistrationRepository;
import com.bondhub.authservice.service.device.DeviceService;
import com.bondhub.authservice.service.otp.OtpService;
import com.bondhub.authservice.service.token.TokenStoreService;
import com.bondhub.authservice.util.TokenProvider;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.constant.MailTemplate;
import com.bondhub.common.dto.client.userservice.user.request.UserCreateRequest;
import com.bondhub.common.enums.Role;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.event.notification.EmailNotificationEvent;
import com.bondhub.common.event.user.UserIndexEvent;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.common.utils.JwtUtil;
import com.bondhub.common.utils.SecurityUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Transactional
public class AuthenticationServiceImpl implements AuthenticationService {

    AccountRepository accountRepository;
    PendingRegistrationRepository pendingRegistrationRepository;
    PasswordEncoder passwordEncoder;
    JwtUtil jwtUtil;
    TokenStoreService tokenStoreService;
    SecurityUtil securityUtil;
    OtpService otpService;
    UserServiceClient userServiceClient;
    MessageSource messageSource;
    TokenProvider tokenProvider;
    OutboxEventPublisher outboxEventPublisher;
    DeviceService deviceService;
    KafkaTemplate<String, Object> kafkaTemplate;

    @NonFinal
    @Value("${kafka.topics.socket-events:socket-events}")
    String socketEventsTopic;

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
            throw new AppException(ErrorCode.AUTH_ACCOUNT_BANNED);
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

        // Record last login timestamp (non-blocking, best-effort)
        try {
            userServiceClient.recordLastLogin(account.getId());
        } catch (Exception e) {
            log.warn("Failed to record last login for accountId={}: {}", account.getId(), e.getMessage());
        }

        return tokenProvider.generateFullTokenResponse(
                account, request.deviceId(), request.deviceType(), userAgent, ipAddress);
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
            throw new AppException(ErrorCode.AUTH_ACCOUNT_BANNED);
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
        String userId = null;
        try {
            if (securityUtil.isAuthenticated()) {
                String jti = securityUtil.getCurrentJwtId();
                userId = securityUtil.getCurrentUserId();
                String accountId = securityUtil.getCurrentAccountId();
                String email = securityUtil.getCurrentEmail();
                long ttl = securityUtil.getRemainingTtlSeconds();

                tokenStoreService.blacklistAccessToken(jti, accountId, email, ttl, "Logout");
            }
        } catch (Exception e) {
            log.warn("Could not blacklist access token during logout: {}", e.getMessage());
        }

        if (refreshToken != null && jwtUtil.validateToken(refreshToken)) {
            String sessionId = jwtUtil.extractSessionId(refreshToken);
            if (sessionId != null) {
                tokenStoreService.revokeRefreshSession(sessionId);
                if (userId != null) {
                    publishForceLogoutEvent(userId, sessionId, "Logout");
                }
            }
        }

        log.info("Logout processed");
    }

    private void publishForceLogoutEvent(String userId, String sessionId, String reason) {
        try {
            Map<String, String> payload = Map.of(
                    "type", "FORCE_LOGOUT",
                    "sessionId", sessionId,
                    "reason", reason);
            kafkaTemplate.send(socketEventsTopic, new SocketEvent(
                    SocketEventType.FORCE_LOGOUT, userId, "/queue/session", payload));
            log.info("[Auth] Published FORCE_LOGOUT event: userId={}, sessionId={}", userId, sessionId);
        } catch (Exception e) {
            log.warn("[Auth] Failed to publish FORCE_LOGOUT event for userId={}: {}", userId, e.getMessage());
        }
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
            .initialInterests(copyInitialInterests(request.initialInterests()))
                .createdAt(now)
                .ttl(300L)
                .build();

        pendingRegistrationRepository.save(pendingReg);

        EmailNotificationEvent emailEvent =
                EmailNotificationEvent.builder()
                .recipientEmail(request.email())
                .subject("Registration Verification")
                .templateId(MailTemplate.REGISTRATION_OTP_TEMPLATE_ID)
                .templateParams(java.util.Map.of(
                        "otpCode", otp,
                        "accountId", "Registration Verification",
                        "companyName", "BondHub",
                        "companyTagline", "Connecting people through shared bonds",
                        "supportEmail", "support@bondhub.com",
                        "currentYear", String.valueOf(java.time.Year.now().getValue())
                ))
                .build();

        outboxEventPublisher.saveAndPublish(
                request.email(),
                "ACCOUNT",
                EventType.EMAIL_NOTIFICATION,
                emailEvent
        );

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

        // Create user profile synchronously via Feign, user-service will handle indexing
        try {
            var createRequest = UserCreateRequest.builder()
                    .accountId(account.getId())
                    .fullName(pendingReg.getFullName())
                    .phoneNumber(account.getPhoneNumber())
                    .role(Role.USER.name())
                    .initialInterests(copyInitialInterests(pendingReg.getInitialInterests()))
                    .build();

            var response = userServiceClient.createUser(createRequest);
            if (response != null && response.data() != null) {
                log.info("✅ User profile created via API for accountId: {}, userId: {}", account.getId(), response.data().id());
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

        EmailNotificationEvent emailEvent =
                EmailNotificationEvent.builder()
                .recipientEmail(request.email())
                .subject("Password Reset Request")
                .templateId(MailTemplate.FORGOT_PASSWORD_OTP_TEMPLATE_ID)
                .templateParams(java.util.Map.of(
                        "otpCode", otp,
                        "accountId", "Password Reset Request",
                        "companyName", "BondHub",
                        "companyTagline", "Connecting people through shared bonds",
                        "supportEmail", "support@bondhub.com",
                        "currentYear", String.valueOf(java.time.Year.now().getValue())
                ))
                .build();

        outboxEventPublisher.saveAndPublish(
                request.email(),
                "ACCOUNT",
                EventType.EMAIL_NOTIFICATION,
                emailEvent
        );

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

    @Override
    public void logoutAllOtherDevices(String refreshToken) {
        if (refreshToken == null || !jwtUtil.validateToken(refreshToken)) {
            throw new AppException(ErrorCode.JWT_INVALID_TOKEN);
        }

        String currentSessionId = jwtUtil.extractSessionId(refreshToken);
        String userId = jwtUtil.extractAccountId(refreshToken);

        if (currentSessionId == null || userId == null) {
            throw new AppException(ErrorCode.JWT_INVALID_TOKEN);
        }

        // --- NEW: enforce root device ---
        enforceRootDeviceAccess(currentSessionId, userId, "logout all other devices");
        // --------------------------------

        log.info("Logging out all other devices for user: {}, currentSessionId: {}", userId, currentSessionId);

        // Revoke all other refresh sessions in Redis (marks revoked=true, keeps the
        // record for JTI lookup)
        List<String> revokedSessionIds = tokenStoreService.revokeAllUserRefreshSessionsExcept(userId, currentSessionId);

        long accessTokenTtlMs = jwtUtil.getAccessTokenExpirationSeconds() * 1000;

        // For each revoked session: blacklist its paired access token + update MongoDB
        // device record
        String presenceUserId = null;
        try {
            presenceUserId = securityUtil.getCurrentUserId();
        } catch (Exception e) {
            log.warn("[Auth] Could not resolve userId for FORCE_LOGOUT broadcast: {}", e.getMessage());
        }

        for (String sessionId : revokedSessionIds) {
            try {
                tokenStoreService.revokeAndBlacklistSession(sessionId, userId, accessTokenTtlMs);

                DeviceUpdateRequest updateRequest = DeviceUpdateRequest.builder()
                        .lastActiveTime(LocalDateTime.now())
                        .build();
                deviceService.updateDeviceBySessionId(sessionId, updateRequest);
                log.debug("Force-logged out session and blacklisted access token: {}", sessionId);

                if (presenceUserId != null) {
                    publishForceLogoutEvent(presenceUserId, sessionId, "LogoutOthers");
                }
            } catch (Exception e) {
                log.warn("Failed to fully process logout for session: {}", sessionId);
            }
        }

        log.info("Logged out {} other devices for user: {}", revokedSessionIds.size(), userId);
    }

    @Override
    public void logoutDevice(String targetSessionId, String currentSessionToken) {
        String currentSessionId = currentSessionToken != null ? jwtUtil.extractSessionId(currentSessionToken) : null;

        // Use the security context (populated by gateway via access token) for caller identity
        String userId = securityUtil.getCurrentAccountId();

        log.info("Request to logout device session {} by user {}", targetSessionId, userId);

        // --- NEW: enforce root device ---
        String rootDeviceId = enforceRootDeviceAccess(currentSessionId, userId, "logout session " + targetSessionId);
        // --------------------------------

        // Verify ownership and existence via TokenStoreService
        RefreshTokenSession session = tokenStoreService
                .findRefreshSession(targetSessionId)
                .orElseThrow(() -> new AppException(ErrorCode.DEV_DEVICE_NOT_FOUND));

        if (!session.getAccountId().equals(userId)) {
            log.warn("User {} attempted to logout session {} belonging to {}", userId, targetSessionId,
                    session.getAccountId());
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        // --- NEW: exclude the root ---
        if (rootDeviceId != null && rootDeviceId.equals(session.getDeviceId())) {
            log.warn("Attempted to explicitly logout root device {}", rootDeviceId);
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        // -----------------------------

        // Revoke the refresh session AND blacklist the paired access token immediately
        long accessTokenTtlMs = jwtUtil.getAccessTokenExpirationSeconds() * 1000;
        tokenStoreService.revokeAndBlacklistSession(targetSessionId, userId, accessTokenTtlMs);

        // Update device in MongoDB
        try {
            DeviceUpdateRequest updateRequest = DeviceUpdateRequest.builder()
                    .lastActiveTime(LocalDateTime.now())
                    .build();
            deviceService.updateDeviceBySessionId(targetSessionId, updateRequest);
            log.info("✅ Device logged out and access token blacklisted: {}", targetSessionId);
        } catch (Exception e) {
            log.warn("⚠️ Could not update device details for session: {}", targetSessionId);
        }

        // Notify the target device to disconnect via WebSocket
        try {
            String presenceUserId = securityUtil.getCurrentUserId();
            if (presenceUserId != null) {
                publishForceLogoutEvent(presenceUserId, targetSessionId, "LogoutDevice");
            } else {
                log.warn("[Auth] Could not resolve userId for FORCE_LOGOUT on logoutDevice");
            }
        } catch (Exception e) {
            log.warn("[Auth] Could not publish FORCE_LOGOUT for logoutDevice: {}", e.getMessage());
        }
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        log.info("Changing password for authenticated user");

        // Get currently authenticated user's ID
        String accountId = securityUtil.getCurrentAccountId();

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND));

        // Verify old password
        if (!passwordEncoder.matches(request.oldPassword(), account.getPassword())) {
            log.warn("Invalid old password for account: {}", accountId);
            throw new AppException(ErrorCode.AUTH_INVALID_OLD_PASSWORD);
        }

        // Validate new password is different from old
        if (request.oldPassword().equals(request.newPassword())) {
            throw new AppException(ErrorCode.AUTH_NEW_PASSWORD_SAME_AS_OLD);
        }

        // Update password
        account.setPassword(passwordEncoder.encode(request.newPassword()));
        accountRepository.save(account);

        log.info("✅ Password successfully changed for account: {}", accountId);
    }

    private Set<String> copyInitialInterests(Set<String> initialInterests) {
        return initialInterests == null ? new HashSet<>() : new HashSet<>(initialInterests);
    }

    private String enforceRootDeviceAccess(String currentSessionId, String userId, String actionDescription) {
        String rootDeviceId = deviceService.getRootMobileDeviceId(userId).orElse(null);

        if (currentSessionId != null && rootDeviceId != null) {
            String currentDeviceId = tokenStoreService.findRefreshSession(currentSessionId)
                    .map(RefreshTokenSession::getDeviceId)
                    .orElse(null);

            if (!rootDeviceId.equals(currentDeviceId)) {
                log.warn("Non-root device {} attempted to {}", currentDeviceId, actionDescription);
                throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
            }
        }

        return rootDeviceId;
    }
}
