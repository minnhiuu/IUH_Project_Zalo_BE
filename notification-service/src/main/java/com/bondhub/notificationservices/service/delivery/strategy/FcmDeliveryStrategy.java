package com.bondhub.notificationservices.service.delivery.strategy;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.notificationservices.client.SocketServiceClient;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.enums.Platform;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.model.UserDevice;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.bondhub.notificationservices.service.delivery.NotificationStrategyHelper;
import com.google.firebase.messaging.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FcmDeliveryStrategy implements NotificationStrategy {

    static final RetryTemplate FCM_RETRY = RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(500, 2, 4000)
            .retryOn(RuntimeException.class)
            .build();

    UserDeviceRepository userDeviceRepository;
    NotificationStrategyHelper strategyHelper;
    SocketServiceClient socketServiceClient;
    S3UtilV2 s3UtilV2;

    @NonFinal
    @Value("${bondhub.frontend-url}")
    String frontendUrl;

    @Override
    public void execute(Notification persisted) {
        String recipientId = persisted.getUserId();

        // For chat messages: only push FCM when user is OFFLINE.
        // When online, messages are already delivered via WebSocket in real-time.
        if (isChatMessageType(persisted.getType())) {
            try {
                boolean isOnline = socketServiceClient.isUserOnline(recipientId);
                if (isOnline) {
                    log.debug("FCM skip: user {} is online, type={}", recipientId, persisted.getType());
                    return;
                }
            } catch (Exception e) {
                // If presence check fails, proceed with FCM (fail-safe: better to send than miss)
                log.warn("FCM presence check failed for user {}, proceeding with push: {}", recipientId, e.getMessage());
            }
        }

        List<UserDevice> devices = userDeviceRepository.findByUserId(recipientId);
        if (devices.isEmpty()) {
            log.debug("FCM skip: no devices for recipientId={}", recipientId);
            return;
        }

        String collapseKey = persisted.getType().name() + "_" + recipientId;
        Map<String, NotificationStrategyHelper.RenderedContent> contentCache = new HashMap<>();

        for (UserDevice device : devices) {
            // Use device-level locale, fallback to "vi"
            String deviceLocale = device.getLocale() != null ? device.getLocale() : "vi";

            // Check device-level notification preference
            if (!isAllowedOnDevice(device, persisted.getType())) {
                log.debug("FCM skip: filtered by device preference: recipient={}, device={}",
                        recipientId, device.getDeviceId());
                continue;
            }

            // DND is already checked at DeliveryService level,
            // but we still check per-device here for granular control
            if (isDeviceInDnd(device)) {
                log.info("FCM skip: silenced by DND: recipient={}, device={}, type={}",
                        recipientId,
                        device.getDeviceId(),
                        persisted.getType()
                );
                continue;
            }

            // Render content for this device's locale (using cache to avoid redundant rendering)
            var rendered = contentCache.computeIfAbsent(deviceLocale, loc -> 
                strategyHelper.render(persisted, NotificationChannel.FCM, loc));

            log.info("FCM processing: type={}, recipientId={}, deviceId={}, locale={}, title='{}', body='{}'",
                    persisted.getType(), recipientId, device.getDeviceId(), deviceLocale, rendered.title(), rendered.body());

            if ("".equals(rendered.title()) && "".equals(rendered.body())) {
                log.warn("FCM skip: both title and body are empty for device={}, type={}", device.getId(), persisted.getType());
                continue;
            }

            sendToDevice(device, rendered.title(), rendered.body(), collapseKey,
                    recipientId, persisted);
        }
    }

    private void sendToDevice(UserDevice device,
                              String title,
                              String body,
                              String collapseKey,
                              String recipientId,
                              Notification persisted) {

        String type = persisted.getType().name();
        String lastActorId = strategyHelper.getStr(persisted, "actorId");
        String lastActorName = strategyHelper.getStr(persisted, "actorName");
        String lastActorAvatar = strategyHelper.getStr(persisted, "actorAvatar");
        String requestId = strategyHelper.getStr(persisted, "requestId");
        int actorCount = persisted.getActorIds() != null ? persisted.getActorIds().size() : 0;
        int othersCount = Math.max(0, actorCount - 1);
        Map<String, Object> notificationPayload = persisted.getPayload();

        String categoryIdentifier = "FRIEND_REQUEST".equals(type) ? "friend_request" : "";

        String url = frontendUrl;
        if (!url.endsWith("/")) url += "/";

        String iconUrl = strategyHelper.resolveAvatar(persisted, s3UtilV2.getS3BaseUrl());
        if (iconUrl == null || iconUrl.isEmpty()) {
            iconUrl = url + "images/logo.jpg";
        }

        Map<String, String> dataPayload = new HashMap<>();
        dataPayload.put("type", type);
        dataPayload.put("title", title != null ? title : "");
        dataPayload.put("body", body != null ? body : "");
        dataPayload.put("actorId", lastActorId != null ? lastActorId : "");
        dataPayload.put("actorName", lastActorName != null ? lastActorName : "");
        dataPayload.put("actorAvatar", iconUrl);
        dataPayload.put("actorCount", String.valueOf(actorCount));
        dataPayload.put("othersCount", String.valueOf(othersCount));
        dataPayload.put("categoryIdentifier", categoryIdentifier);
        dataPayload.put("requestId", requestId != null ? requestId : "");
        dataPayload.put("url", url);

        // For CALL notifications, include custom payload fields
        boolean includeCustomPayload = "CALL".equals(type);
        if (includeCustomPayload && notificationPayload != null) {
            for (Map.Entry<String, Object> entry : notificationPayload.entrySet()) {
                String key = entry.getKey();
                // Skip standard fields already added
                if (!dataPayload.containsKey(key) && entry.getValue() != null) {
                    dataPayload.put(key, entry.getValue().toString());
                }
            }
        }

        if (device.getPlatform() == Platform.ANDROID) {
            dataPayload.put("customTitle", title != null ? title : "");
            dataPayload.put("customBody", body != null ? body : "");
            dataPayload.remove("title");
            dataPayload.remove("body");
        }

        Message.Builder messageBuilder = Message.builder()
                .setToken(device.getFcmToken())
                .putAllData(dataPayload);

        if (device.getPlatform() == Platform.WEB) {
            log.info("[FCM] Sending data-only message to WEB with icon: {}", iconUrl);
            
            messageBuilder.setWebpushConfig(WebpushConfig.builder()
                    .setFcmOptions(WebpushFcmOptions.withLink(url))
                    .build());
        }

        // if (device.getPlatform() == Platform.ANDROID || device.getPlatform() == Platform.IOS || device.getPlatform() == Platform.WEB) {
        //      messageBuilder.setNotification(com.google.firebase.messaging.Notification.builder()
        //             .setTitle(title)
        //             .setBody(body)
        //             .build());
        // }

        if (device.getPlatform() == Platform.ANDROID) {
            messageBuilder.setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setCollapseKey(collapseKey)
                    .build());
        }

        if (device.getPlatform() == Platform.IOS) {
            messageBuilder.setApnsConfig(ApnsConfig.builder()
                    .setAps(Aps.builder()
                            .setCategory(categoryIdentifier.isEmpty() ? null : categoryIdentifier)
                            .setThreadId(collapseKey)
                            .setContentAvailable(true)
                            .setMutableContent(true)
                            .setSound("default")
                            .build())
                    .build());
        }

        Message message = messageBuilder.build();

        try {
            String messageId = FCM_RETRY.execute(ctx -> {
                try {
                    return FirebaseMessaging.getInstance().send(message);
                } catch (FirebaseMessagingException e) {
                    MessagingErrorCode code = e.getMessagingErrorCode();
                    if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                        log.warn("FCM stale token, removing device: recipientId={}, deviceId={}, code={}",
                                recipientId, device.getId(), code);
                        userDeviceRepository.delete(device);
                        return null;
                    }
                    log.warn("FCM transient error [attempt {}]: recipientId={}, deviceId={}, error={}",
                            ctx.getRetryCount() + 1, recipientId, device.getId(), e.getMessage());
                    throw new RuntimeException("FCM transient error: " + e.getMessage(), e);
                }
            });
            if (messageId != null) {
                log.info("FCM sent: recipientId={}, deviceId={}, messageId={}, actorCount={}",
                        recipientId, device.getId(), messageId, actorCount);
            }
        } catch (Exception e) {
            log.error("FCM failed after max retries: recipientId={}, deviceId={}, error={}",
                    recipientId, device.getId(), e.getMessage());
        }
    }

    private boolean isChatMessageType(NotificationType type) {
        return type == NotificationType.MESSAGE_DIRECT ||
               type == NotificationType.MESSAGE_GROUP ||
               type == NotificationType.CALL;
    }

    private boolean isAllowedOnDevice(UserDevice device, NotificationType type) {
        if (!device.isAllowNotifications()) return false;
        return switch (type) {
            case FRIEND_REQUEST -> device.isNotifFriendRequests();
            case MESSAGE_DIRECT -> device.isNotifMessages();
            case MESSAGE_GROUP -> device.isNotifGroups();
            default -> true;
        };
    }

    private boolean isDeviceInDnd(UserDevice device) {
        if (!device.isDndEnabled()) return false;
        if (device.getDndStartTime() == null || device.getDndEndTime() == null) return false;
        try {
            String tz = device.getDndTimezone() != null ? device.getDndTimezone() : "Asia/Ho_Chi_Minh";
            if (tz.startsWith("GMT") && (tz.contains("+") || tz.contains("-"))) {
                String prefix = tz.substring(0, 4);
                String offset = tz.substring(4);
                if (!offset.contains(":")) {
                    if (offset.length() == 1) offset = "0" + offset + ":00";
                    else if (offset.length() == 2) offset = offset + ":00";
                } else {
                    String[] parts = offset.split(":");
                    if (parts[0].length() == 1) offset = "0" + offset;
                }
                tz = prefix + offset;
            }
            ZonedDateTime nowZoned = ZonedDateTime.now(ZoneId.of(tz));
            LocalTime now = nowZoned.toLocalTime();
            LocalTime start = LocalTime.parse(device.getDndStartTime());
            LocalTime end = LocalTime.parse(device.getDndEndTime());
            List<DayOfWeek> activeDays = device.getActiveDays();
            boolean hasActiveDays = activeDays != null && !activeDays.isEmpty();
            if (start.isBefore(end)) {
                if (hasActiveDays && !activeDays.contains(nowZoned.getDayOfWeek())) return false;
                return !now.isBefore(start) && now.isBefore(end);
            }
            if (!now.isBefore(start)) {
                return !hasActiveDays || activeDays.contains(nowZoned.getDayOfWeek());
            }
            if (now.isBefore(end)) {
                return !hasActiveDays || activeDays.contains(nowZoned.minusDays(1).getDayOfWeek());
            }
            return false;
        } catch (Exception e) {
            log.warn("FCM DND check failed: deviceId={}, error={}", device.getDeviceId(), e.getMessage());
            return false;
        }
    }
}
