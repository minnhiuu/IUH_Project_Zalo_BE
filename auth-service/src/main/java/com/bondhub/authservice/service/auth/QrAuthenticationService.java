package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.dto.auth.request.QrMobileRequest;
import com.bondhub.authservice.dto.auth.response.QrGenerationResponse;
import com.bondhub.authservice.dto.auth.response.QrStatusResponse;

public interface QrAuthenticationService {

    QrGenerationResponse generateQr(String deviceId, String userAgent, String ipAddress);

    void scanQr(QrMobileRequest request);

    void acceptQr(QrMobileRequest request);

    void rejectQr(QrMobileRequest request);

    String extractQrId(String qrContent);
}
