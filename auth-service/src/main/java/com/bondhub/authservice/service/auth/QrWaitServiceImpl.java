package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.config.QrProperties;
import com.bondhub.authservice.dto.auth.response.QrStatusResponse;
import com.bondhub.authservice.enums.QrSessionStatus;
import com.bondhub.authservice.model.redis.QrSession;
import com.bondhub.authservice.repository.redis.QrSessionRepository;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class QrWaitServiceImpl implements QrWaitService{

    QrProperties qrProperties;
    Map<String, List<DeferredResult<QrStatusResponse>>> qrStatusMap = new ConcurrentHashMap<>();
    QrSessionRepository qrSessionRepository;

    @Override
    public DeferredResult<QrStatusResponse> waitForUpdateQrStatus(String qrId, QrSessionStatus expectedStatus) {
        long defaultTimeout = qrProperties.getWaitTimeoutMs() > 0 ? qrProperties.getWaitTimeoutMs() : 20000;
        
        QrSession qrSession = qrSessionRepository.findById(qrId)
                .orElseThrow(() -> new AppException(ErrorCode.QR_SESSION_EXPIRED));

        long actualTimeout = defaultTimeout;
        if (qrSession.getExpiresAt() != null) {
            long timeUntilExpiry = Duration.between(LocalDateTime.now(), qrSession.getExpiresAt()).toMillis();
            
            if (timeUntilExpiry <= 0) {
                throw new AppException(ErrorCode.QR_SESSION_EXPIRED);
            }
            actualTimeout = Math.min(defaultTimeout, timeUntilExpiry);
        }

        DeferredResult<QrStatusResponse> deferredResult = new DeferredResult<>(actualTimeout);

        if (hasReachedStatus(qrSession.getStatus(), expectedStatus) || qrSession.getStatus() == QrSessionStatus.REJECTED) {
            deferredResult.setResult(mapToResponse(qrSession));
            return deferredResult;
        }

        qrStatusMap.computeIfAbsent(qrId, k -> new CopyOnWriteArrayList<>()).add(deferredResult);

        deferredResult.onTimeout(() -> {
            if (!deferredResult.isSetOrExpired()) {
                deferredResult.setResult(mapToResponse(qrSession));
            }
        });

        deferredResult.onCompletion(() -> {
            removeWaiter(qrId, deferredResult);
        });

        return deferredResult;
    }

    private void removeWaiter(String qrId, DeferredResult<QrStatusResponse> deferredResult) {
        java.util.List<DeferredResult<QrStatusResponse>> waiters = qrStatusMap.get(qrId);
        if (waiters != null) {
            waiters.remove(deferredResult);
            if (waiters.isEmpty()) {
                qrStatusMap.remove(qrId);
            }
        }
    }

    private boolean hasReachedStatus(QrSessionStatus actual, QrSessionStatus expected) {

        if(expected == QrSessionStatus.SCANNED) {
            return actual == QrSessionStatus.SCANNED || actual == QrSessionStatus.CONFIRMED;
        }

        if(expected == QrSessionStatus.CONFIRMED) {
            return actual == QrSessionStatus.CONFIRMED;
        }

        return false;
    }

    @Override
    public void notifyUpdateQrStatus(String qrId, QrSession qrSession) {
        List<DeferredResult<QrStatusResponse>> waiters = qrStatusMap.remove(qrId);

        if (waiters != null) {
            QrStatusResponse response = mapToResponse(qrSession);
            for (DeferredResult<QrStatusResponse> deferredResult : waiters) {
                if (!deferredResult.isSetOrExpired()) {
                    deferredResult.setResult(response);
                }
            }
        }
    }

    private QrStatusResponse mapToResponse(QrSession session) {
        return QrStatusResponse.builder()
                .status(session.getStatus())
                .accessToken(session.getWebAccessToken())
                .refreshToken(session.getWebRefreshToken())
                .refreshTokenExpirationMs(session.getRefreshTokenExpirationMs())
                .userAvatar(session.getUserAvatar())
                .userFullName(session.getUserFullName())
                .build();
    }
}
