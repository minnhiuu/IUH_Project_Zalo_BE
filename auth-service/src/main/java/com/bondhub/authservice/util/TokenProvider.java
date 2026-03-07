package com.bondhub.authservice.util;

import com.bondhub.authservice.client.UserServiceClient;
import com.bondhub.authservice.dto.auth.response.TokenResponse;
import com.bondhub.authservice.dto.device.request.DeviceCreateRequest;
import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.model.Account;
import com.bondhub.authservice.service.device.DeviceService;
import com.bondhub.authservice.service.token.TokenStoreService;

import com.bondhub.common.utils.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TokenProvider {

    JwtUtil jwtUtil;
    TokenStoreService tokenStoreService;
    UserServiceClient userServiceClient;
    DeviceService deviceService;

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

        String accessTokenJti = jwtUtil.extractJti(accessToken);

        tokenStoreService.createRefreshSession(
                sessionId,
                account.getId(),
                account.getPhoneNumber(),
                deviceId,
                deviceType,
                refreshToken,
                accessTokenJti,
                userAgent,
                ipAddress,
                refreshExpirationMs / 1000);

        // Persist / update the device in MongoDB
        try {
            String parsedOs = UserAgentParser.parseOs(userAgent, deviceType != null ? deviceType.name() : null);
            String parsedBrowser = UserAgentParser.parseBrowser(userAgent);
            String deviceName = parsedBrowser + " on " + parsedOs;

            DeviceCreateRequest deviceRequest = new DeviceCreateRequest(
                    deviceId,
                    sessionId,
                    deviceName,
                    parsedBrowser,
                    parsedOs,
                    deviceType,
                    ipAddress,
                    LocalDateTime.now(),
                    account.getId());

            deviceService.saveOrUpdateDevice(deviceRequest);
            log.info("Device saved/updated in MongoDB for accountId: {}, sessionId: {}", account.getId(), sessionId);
        } catch (Exception e) {
            log.warn("Failed to save device in MongoDB for accountId: {}, reason: {}", account.getId(), e.getMessage());
        }

        return TokenResponse.of(accessToken, refreshToken, refreshExpirationMs);
    }
}
