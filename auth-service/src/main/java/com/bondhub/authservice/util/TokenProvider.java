package com.bondhub.authservice.util;

import com.bondhub.authservice.client.UserServiceClient;
import com.bondhub.authservice.dto.auth.response.TokenResponse;
import com.bondhub.authservice.dto.device.request.DeviceCreateRequest;
import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.model.Account;
import com.bondhub.authservice.model.redis.RefreshTokenSession;
import com.bondhub.authservice.service.device.DeviceService;
import com.bondhub.authservice.service.token.TokenStoreService;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.common.publisher.RawNotificationEventPublisher;
import com.bondhub.common.utils.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    RawNotificationEventPublisher rawNotificationEventPublisher;
    KafkaTemplate<String, Object> kafkaTemplate;

    @NonFinal
    @Value("${kafka.topics.socket-events:socket-events}")
    String socketEventsTopic;

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

        List<RefreshTokenSession> kickedSessions = tokenStoreService.createRefreshSession(
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

        // For web logins: force-logout any previously active web sessions
        if (deviceType == DeviceType.WEB && userId != null && !kickedSessions.isEmpty()) {
            forceLogoutKickedWebSessions(kickedSessions, account.getId(), userId);
        }

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

            // Notify root mobile device about a new WEB login (best-effort, fire-and-forget)
            if (deviceType == DeviceType.WEB && userId != null) {
                publishNewWebLoginNotification(userId, account.getId(), sessionId, deviceName, ipAddress);
            }
        } catch (Exception e) {
            log.warn("Failed to save device in MongoDB for accountId: {}, reason: {}", account.getId(), e.getMessage());
        }

        long accessTokenTtlSeconds = jwtUtil.getAccessTokenExpirationSeconds();
        tokenStoreService.updateSessionAccessToken(sessionId, accessTokenJti,
                System.currentTimeMillis() + (accessTokenTtlSeconds * 1000));

        return TokenResponse.of(accessToken, refreshToken, refreshExpirationMs);
    }

    /**
     * Blacklists the access token and sends a FORCE_LOGOUT socket event for every
     * web session that was displaced by the new login.  Fire-and-forget; any single
     * failure is logged and swallowed so it never blocks the login response.
     */
    private void forceLogoutKickedWebSessions(List<RefreshTokenSession> kicked, String accountId, String userId) {
        long accessTokenTtlMs = jwtUtil.getAccessTokenExpirationSeconds() * 1000;
        for (RefreshTokenSession session : kicked) {
            try {
                String jti = session.getAccessTokenJti();
                if (jti != null && !jti.isBlank() && accessTokenTtlMs > 0) {
                    tokenStoreService.blacklistAccessToken(jti, accountId, null,
                            accessTokenTtlMs / 1000, "NewWebLogin");
                }

                Map<String, String> payload = Map.of(
                        "type", "FORCE_LOGOUT",
                        "sessionId", session.getSessionId(),
                        "reason", "NewWebLogin");
                kafkaTemplate.send(socketEventsTopic, new SocketEvent(
                        SocketEventType.FORCE_LOGOUT, userId, "/queue/session", payload));
                log.info("[Auth] Published FORCE_LOGOUT for displaced web session: userId={}, sessionId={}",
                        userId, session.getSessionId());
            } catch (Exception e) {
                log.warn("[Auth] Failed to force-logout kicked web session={}: {}", session.getSessionId(), e.getMessage());
            }
        }
    }

    /**
     * Publishes a {@code NEW_DEVICE_LOGIN} raw notification event to the pipeline.
     * The event includes {@code rootDeviceId} — the auth-service Device.deviceId of
     * the account's designated root mobile device — so that {@code FcmDeliveryStrategy}
     * can look up the exact FCM record in notification-service without any cross-service
     * calls at delivery time. This is fire-and-forget; failures are logged and swallowed.
     */
    private void publishNewWebLoginNotification(String userId, String accountId, String sessionId,
                                                String deviceName, String ipAddress) {
        try {
            // Resolve the root mobile device's deviceId (from auth-service's Device collection)
            String rootDeviceId = deviceService.getRootMobileDeviceId(accountId).orElse(null);
            if (rootDeviceId == null) {
                log.debug("[Auth] NEW_DEVICE_LOGIN skip: no root mobile device for accountId={}", accountId);
                return;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("deviceName", deviceName);
            payload.put("ipAddress", ipAddress != null ? ipAddress : "Unknown");
            payload.put("loginTime", LocalDateTime.now().toString());
            payload.put("rootDeviceId", rootDeviceId);   // used by FcmDeliveryStrategy to find the FCM record
            payload.put("sessionId", sessionId);

            RawNotificationEvent event = RawNotificationEvent.builder()
                    .recipientId(userId)
                    .type(NotificationType.NEW_DEVICE_LOGIN)
                    .referenceId(sessionId)
                    .payload(payload)
                    .occurredAt(LocalDateTime.now())
                    .build();

            rawNotificationEventPublisher.publish(event);
            log.info("[Auth] Published NEW_DEVICE_LOGIN event: userId={}, rootDeviceId={}, sessionId={}",
                    userId, rootDeviceId, sessionId);
        } catch (Exception e) {
            log.warn("[Auth] Failed to publish NEW_DEVICE_LOGIN for userId={}: {}", userId, e.getMessage());
        }
    }
}
