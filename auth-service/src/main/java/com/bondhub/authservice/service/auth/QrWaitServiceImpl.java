package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.client.UserServiceClient;
import com.bondhub.authservice.config.QrProperties;
import com.bondhub.authservice.dto.auth.response.QrStatusResponse;
import com.bondhub.authservice.enums.QrSessionStatus;
import com.bondhub.authservice.model.redis.QrSession;
import com.bondhub.authservice.repository.AccountRepository;
import com.bondhub.authservice.repository.redis.QrSessionRepository;
import com.bondhub.authservice.util.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class QrWaitServiceImpl implements QrWaitService{

    QrProperties qrProperties;
    Map<String, DeferredResult<QrStatusResponse>> qrStatusMap = new ConcurrentHashMap<>();
    QrSessionRepository qrSessionRepository;

    @Override
    public DeferredResult<QrStatusResponse> waitForUpdateQrStatus(String qrId, QrSessionStatus expectedStatus) {
        DeferredResult<QrStatusResponse> deferredResult = new DeferredResult<>(qrProperties.getWaitTimeoutMs());

        QrSession qrSession = qrSessionRepository.findById(qrId).orElseThrow(() -> new AppException(ErrorCode.QR_SESSION_EXPIRED));

        if(hasReachedStatus(qrSession.getStatus(), expectedStatus) || qrSession.getStatus() == QrSessionStatus.REJECTED){
            deferredResult.setResult(mapToResponse(qrSession));
            return deferredResult;
        }

        qrStatusMap.put(qrId, deferredResult);

        deferredResult.onTimeout(() -> {
            qrStatusMap.remove(qrId);
            if (!deferredResult.isSetOrExpired()) {
                deferredResult.setResult(mapToResponse(qrSession));
            }
        });

        deferredResult.onCompletion(() -> {
            qrStatusMap.remove(qrId);
        });

        return deferredResult;
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
        DeferredResult<QrStatusResponse> deferredResult = qrStatusMap.remove(qrId);

        if(deferredResult != null && !deferredResult.isSetOrExpired()) {
            deferredResult.setResult(mapToResponse(qrSession));
        }
    }

    private QrStatusResponse mapToResponse(QrSession session) {
        return QrStatusResponse.builder()
                .status(session.getStatus())
                .accessToken(session.getWebAccessToken())
                .refreshToken(session.getWebRefreshToken())
                .userAvatar(session.getUserAvatar())
                .userFullName(session.getUserFullName())
                .build();
    }
}
