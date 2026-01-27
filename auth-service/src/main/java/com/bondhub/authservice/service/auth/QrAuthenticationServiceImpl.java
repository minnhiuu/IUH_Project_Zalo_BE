package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.client.UserServiceClient;
import com.bondhub.authservice.config.QrProperties;
import com.bondhub.authservice.dto.auth.request.QrMobileRequest;
import com.bondhub.authservice.dto.auth.response.QrGenerationResponse;
import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.enums.QrSessionStatus;
import com.bondhub.authservice.model.Account;
import com.bondhub.authservice.model.redis.QrSession;
import com.bondhub.authservice.repository.AccountRepository;
import com.bondhub.authservice.repository.redis.QrSessionRepository;
import com.bondhub.authservice.service.token.TokenStoreService;
import com.bondhub.authservice.util.SecurityUtil;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class QrAuthenticationServiceImpl implements QrAuthenticationService {

    QrProperties qrProperties;
    SecurityUtil securityUtil;
    JwtUtil jwtUtil;

    TokenStoreService tokenStoreService;
    QrWaitService qrWaitService;
    UserServiceClient userServiceClient;

    QrSessionRepository qrSessionRepository;
    AccountRepository accountRepository;

    @Override
    public QrGenerationResponse generateQr(String deviceId, String userAgent, String ipAddress) {
        String qrId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(qrProperties.getExpirationSeconds());

        QrSession session = QrSession.builder()
                .id(qrId)
                .status(QrSessionStatus.PENDING)
                .ttl(qrProperties.getExpirationSeconds())
                .deviceId(deviceId)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .build();

        qrSessionRepository.save(session);
        log.info("Generated QR session: {} for device: {}", qrId, deviceId);

        return QrGenerationResponse.builder()
                .qrId(qrId)
                .expiresAt(expiresAt)
                .qrContent(qrProperties.getContentPrefix() + qrId)
                .build();
    }

    @Override
    public void scanQr(QrMobileRequest request) {
        String currentAccountId = securityUtil.getCurrentAccountId();
        String qrId = extractQrId(request.qrContent());
        QrSession session = qrSessionRepository.findById(qrId)
                .orElseThrow(() -> new AppException(ErrorCode.QR_SESSION_EXPIRED));

        if (session.getStatus() != QrSessionStatus.PENDING) {
            throw new AppException(ErrorCode.QR_SESSION_INVALID_STATE);
        }

        try {
            ApiResponse<UserSummaryResponse> response = userServiceClient.getUserSummaryByAccountId(currentAccountId);

            if (response != null && response.data() != null) {
                UserSummaryResponse userSummary = response.data();
                session.setUserAvatar(userSummary.getAvatar());
                session.setUserFullName(userSummary.getFullName());
            }
        } catch (Exception e) {
            log.error("Error fetching user summary for account: {}", currentAccountId, e);
            Account account = accountRepository.findById(currentAccountId).orElseThrow();
            session.setUserFullName(account.getEmail());
            session.setUserAvatar("https://ui-avatars.com/api/?name=" + account.getEmail());
        }

        session.setStatus(QrSessionStatus.SCANNED);
        session.setAccountId(currentAccountId);

        qrSessionRepository.save(session);
        qrWaitService.notifyUpdateQrStatus(qrId, session);
        log.info("QR session {} scanned by user {}", qrId, currentAccountId);
    }

    @Override
    public void acceptQr(QrMobileRequest request) {
        String currentAccountId = securityUtil.getCurrentAccountId();
        String qrId = extractQrId(request.qrContent());
        QrSession session = qrSessionRepository.findById(qrId)
                .orElseThrow(() -> new AppException(ErrorCode.QR_SESSION_EXPIRED));

        if (session.getStatus() != QrSessionStatus.SCANNED) {
            throw new AppException(ErrorCode.QR_SESSION_INVALID_STATE);
        }

        if (!currentAccountId.equals(session.getAccountId())) {
            throw new AppException(ErrorCode.QR_SESSION_UNAUTHORIZED);
        }

        Account account = accountRepository.findById(currentAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND));

        String sessionId = UUID.randomUUID().toString();
        long refreshExpirationMs = jwtUtil.getWebRefreshExpirationMs();

        String accessToken = jwtUtil.generateAccessToken(
                account.getId(),
                account.getEmail(),
                account.getRole(),
                sessionId
        );
        String refreshToken = jwtUtil.generateRefreshToken(
                account.getId(),
                sessionId,
                refreshExpirationMs
        );

        tokenStoreService.createRefreshSession(
                sessionId,
                account.getId(),
                account.getPhoneNumber(),
                session.getDeviceId(),
                DeviceType.WEB,
                refreshToken,
                session.getUserAgent(),
                session.getIpAddress(),
                refreshExpirationMs / 1000
        );

        session.setStatus(QrSessionStatus.CONFIRMED);
        session.setWebAccessToken(accessToken);
        session.setWebRefreshToken(refreshToken);

        qrSessionRepository.save(session);
        qrWaitService.notifyUpdateQrStatus(qrId, session);
        log.info("QR session {} confirmed by user {}", qrId, currentAccountId);
    }

    @Override
    public void rejectQr(QrMobileRequest request) {
        String currentAccountId = securityUtil.getCurrentAccountId();
        String qrId = extractQrId(request.qrContent());
        QrSession session = qrSessionRepository.findById(qrId)
                .orElseThrow(() -> new AppException(ErrorCode.QR_SESSION_EXPIRED));

        if (session.getStatus() != QrSessionStatus.SCANNED) {
            throw new AppException(ErrorCode.QR_SESSION_INVALID_STATE);
        }

        if (!currentAccountId.equals(session.getAccountId())) {
            throw new AppException(ErrorCode.QR_SESSION_UNAUTHORIZED);
        }

        session.setStatus(QrSessionStatus.REJECTED);
        qrSessionRepository.save(session);
        qrWaitService.notifyUpdateQrStatus(qrId, session);
        log.info("QR session {} rejected by user {}", qrId, currentAccountId);
    }

    @Override
    public String extractQrId(String qrContent) {
        if (qrContent == null || qrContent.isBlank()) {
            throw new AppException(ErrorCode.QR_SESSION_INVALID_STATE);
        }
        
        if (qrContent.startsWith(qrProperties.getContentPrefix())) {
            String extractedId = qrContent.substring(qrProperties.getContentPrefix().length());
            if (extractedId.isBlank()) {
                throw new AppException(ErrorCode.QR_SESSION_INVALID_STATE);
            }
            return extractedId;
        }
        
        return qrContent;
    }
}
