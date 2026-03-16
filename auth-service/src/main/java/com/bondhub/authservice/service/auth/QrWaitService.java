package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.dto.auth.response.QrStatusResponse;
import com.bondhub.authservice.enums.QrSessionStatus;
import com.bondhub.authservice.model.redis.QrSession;
import org.springframework.web.context.request.async.DeferredResult;

public interface QrWaitService {

    DeferredResult<QrStatusResponse> waitForUpdateQrStatus(String qrId, QrSessionStatus expectedStatus);

    void notifyUpdateQrStatus(String qrId, QrSession qrSession);
}
