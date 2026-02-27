package com.bondhub.authservice.util;

import com.bondhub.authservice.client.UserServiceClient;
import com.bondhub.authservice.dto.auth.response.TokenResponse;
import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.model.Account;
import com.bondhub.authservice.service.token.TokenStoreService;
import com.bondhub.common.enums.Role;
import com.bondhub.common.utils.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TokenProvider {

    JwtUtil jwtUtil;
    TokenStoreService tokenStoreService;
    UserServiceClient userServiceClient;

    public TokenResponse generateFullTokenResponse(Account account, String deviceId, DeviceType deviceType,
                                                   String userAgent, String ipAddress) {
        String sessionId = UUID.randomUUID().toString();

        long refreshExpirationMs = (deviceType == DeviceType.MOBILE)
                ? jwtUtil.getMobileRefreshExpirationMs()
                : jwtUtil.getWebRefreshExpirationMs();

        String userId = null;
        try {
            var response = userServiceClient.getUserSummaryByAccountId(account.getId());
            if (response != null && response.data() != null) {
                userId = response.data().id();
            }
        } catch (Exception e) {
            log.warn("Could not fetch user profile ID for accountId: {}, reason: {}", account.getId(), e.getMessage());
        }

        String accessToken = jwtUtil.generateAccessToken(account.getId(), userId, account.getEmail(), account.getRole(),
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

        return TokenResponse.of(accessToken, refreshToken, refreshExpirationMs);
    }
}
