package com.bondhub.notificationservices.service.delivery.strategy;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.enums.Platform;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.model.UserDevice;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.bondhub.notificationservices.service.template.NotificationTemplateService;
import com.bondhub.notificationservices.service.user.preference.UserPreferenceService;
import com.bondhub.notificationservices.dto.response.template.NotificationTemplateResponse;
import com.google.firebase.messaging.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    NotificationTemplateService templateService;
    UserPreferenceService userPreferenceService;

    @NonFinal
    @Value("${bondhub.frontend-url}")
    String frontendUrl;

    @Override
    public void execute(Notification persisted) {
        String recipientId = persisted.getUserId();

        // NEW_DEVICE_LOGIN: target only the user's root mobile device
        if (persisted.getType() == NotificationType.NEW_DEVICE_LOGIN) {
            executeForRootDevice(persisted, recipientId);
            return;
        }

        List<UserDevice> devices = userDeviceRepository.findByUserId(recipientId);
        if (devices.isEmpty()) {
            log.debug("FCM skip: no devices for recipientId={}", recipientId);
            return;
        }

        var userPrefs = userPreferenceService.getPreferences(recipientId);
        String globalLocale = (userPrefs != null) ? userPrefs.getLanguage() : "vi";

        int actorCount = persisted.getActorIds() != null ? persisted.getActorIds().size() : 0;
        int othersCount = Math.max(0, actorCount - 1);

        String lastActorId = getStr(persisted, "actorId");
        String lastActorName = getStr(persisted, "actorName");
        String lastActorAvatar = getStr(persisted, "actorAvatar");
        String requestId = getStr(persisted, "requestId");

        Map<String, Object> baseRenderData = new HashMap<>(persisted.getPayload() != null ? persisted.getPayload() : Collections.emptyMap());
        baseRenderData.put("actorCount", actorCount);
        baseRenderData.put("othersCount", actorCount > 2 ? actorCount - 1 : 0);
        baseRenderData.put("showSecondActor", actorCount == 2);
        baseRenderData.put("actorName", lastActorName != null ? lastActorName : "");
        baseRenderData.put("actorAvatar", lastActorAvatar != null ? lastActorAvatar : "");

        if (actorCount == 2) {
            String secondActorName = getStr(persisted, "secondActorName");
            baseRenderData.put("secondActorName", secondActorName != null ? secondActorName : "một người khác");
        }

        String collapseKey = persisted.getType().name() + "_" + recipientId;

        Map<String, NotificationTemplateResponse> templateCache = new HashMap<>();

        for (UserDevice device : devices) {
            String deviceLocale = device.getLocale();
            
            if (deviceLocale == null) {
                Map<String, String> deviceLocales = userPrefs != null && userPrefs.getLanguageByDeviceId() != null 
                        ? userPrefs.getLanguageByDeviceId() 
                        : Map.of();
                deviceLocale = deviceLocales.getOrDefault(device.getDeviceId(), globalLocale);
            }
            
            if (deviceLocale == null) deviceLocale = "vi";
            
            if (!userPreferenceService.allow(userPrefs, device.getDeviceId(), persisted.getType())) {
                log.debug("FCM skip: filtered by device preference: recipient={}, device={}", recipientId, device.getDeviceId());
                continue;
            }

            Map<String, Object> deviceRenderData = new HashMap<>(baseRenderData);
            
            String localeKey = deviceLocale.toLowerCase();
            if (deviceRenderData.containsKey("message_" + localeKey)) {
                deviceRenderData.put("message", deviceRenderData.get("message_" + localeKey));
            }
            if (deviceRenderData.containsKey("title_" + localeKey)) {
                deviceRenderData.put("title", deviceRenderData.get("title_" + localeKey));
            }

            var template = templateCache.computeIfAbsent(deviceLocale, loc -> 
                templateService.getTemplate(persisted.getType(), NotificationChannel.FCM, loc));

            String title = templateService.render(template.titleTemplate(), deviceRenderData);
            String body = templateService.render(template.bodyTemplate(), deviceRenderData);

            log.info("FCM processing: type={}, recipientId={}, deviceId={}, locale={}, title='{}', body='{}'",
                    persisted.getType(), recipientId, device.getDeviceId(), deviceLocale, title, body);

            if ("".equals(title) && "".equals(body)) {
                log.warn("FCM skip: both title and body are empty for device={}, type={}", device.getId(), persisted.getType());
                continue;
            }

            sendToDevice(device, title, body, collapseKey,
                    recipientId, lastActorId, lastActorName, lastActorAvatar,
                    actorCount, othersCount, persisted.getType().name(), requestId,
                    persisted.getPayload());
        }
    }

    private void sendToDevice(UserDevice device,
                              String title,
                              String body,
                              String collapseKey,
                              String recipientId,
                              String lastActorId,
                              String lastActorName,
                              String lastActorAvatar,
                              int actorCount,
                              int othersCount,
                              String type,
                              String requestId,
                              Map<String, Object> notificationPayload) {

        String categoryIdentifier = "FRIEND_REQUEST".equals(type) ? "friend_request" : "";

        String url = frontendUrl;
        if (!url.endsWith("/")) url += "/";

        Map<String, String> dataPayload = new HashMap<>();
        dataPayload.put("type", type);
        dataPayload.put("title", title != null ? title : "");
        dataPayload.put("body", body != null ? body : "");
        dataPayload.put("actorId", lastActorId != null ? lastActorId : "");
        dataPayload.put("actorName", lastActorName != null ? lastActorName : "");
        dataPayload.put("actorAvatar", lastActorAvatar != null ? lastActorAvatar : "");
        dataPayload.put("actorCount", String.valueOf(actorCount));
        dataPayload.put("othersCount", String.valueOf(othersCount));
        dataPayload.put("categoryIdentifier", categoryIdentifier);
        dataPayload.put("requestId", requestId != null ? requestId : "");
        dataPayload.put("url", url);

        // For CALL, moderation, and login notifications, include custom payload fields
        boolean includeCustomPayload = "CALL".equals(type)
                || "CONTENT_REMOVED".equals(type)
                || "CONTENT_HIDDEN".equals(type)
                || "USER_WARNED".equals(type)
                || "NEW_DEVICE_LOGIN".equals(type);
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
            String iconUrl = lastActorAvatar != null && !lastActorAvatar.isEmpty() ? lastActorAvatar : "/images/logo.jpg";
            log.info("[FCM] Sending data-only message to WEB with icon: {}", iconUrl);
            
            messageBuilder.setWebpushConfig(WebpushConfig.builder()
                    .setFcmOptions(WebpushFcmOptions.withLink(url))
                    .build());
        }

//        if (device.getPlatform() == Platform.ANDROID || device.getPlatform() == Platform.IOS) {
//             messageBuilder.setNotification(com.google.firebase.messaging.Notification.builder()
//                    .setTitle(title)
//                    .setBody(body)
//                    .build());
//        }

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

    /**
     * Delivers a {@code NEW_DEVICE_LOGIN} push notification exclusively to the
     * user's root mobile device. The {@code rootDeviceId} is set by the auth-service
     * in the notification payload at publish time (taken from the auth-service
     * {@code Device} record marked {@code isRootDevice=true}).
     * If the root device has no registered FCM token, delivery is silently skipped.
     */
    private void executeForRootDevice(Notification persisted, String recipientId) {
        String rootDeviceId = getStr(persisted, "rootDeviceId");
        if (rootDeviceId == null || rootDeviceId.isBlank()) {
            log.debug("[FCM] NEW_DEVICE_LOGIN skip: rootDeviceId absent in payload for recipientId={}", recipientId);
            return;
        }

        Optional<UserDevice> deviceOpt = userDeviceRepository.findByUserIdAndDeviceId(recipientId, rootDeviceId);
        if (deviceOpt.isEmpty()) {
            log.debug("[FCM] NEW_DEVICE_LOGIN skip: no FCM registration found for recipientId={}, rootDeviceId={}",
                    recipientId, rootDeviceId);
            return;
        }

        UserDevice rootDevice = deviceOpt.get();
        var userPrefs = userPreferenceService.getPreferences(recipientId);

        if (!userPreferenceService.allow(userPrefs, rootDevice.getDeviceId(), persisted.getType())) {
            log.debug("[FCM] NEW_DEVICE_LOGIN skip: filtered by preference for recipientId={}", recipientId);
            return;
        }

        String globalLocale = (userPrefs != null) ? userPrefs.getLanguage() : "vi";
        String deviceLocale = rootDevice.getLocale() != null ? rootDevice.getLocale() : globalLocale;
        if (deviceLocale == null) deviceLocale = "vi";

        Map<String, Object> renderData = new HashMap<>(
                persisted.getPayload() != null ? persisted.getPayload() : Collections.emptyMap());

        var template = templateService.getTemplate(persisted.getType(), NotificationChannel.FCM, deviceLocale);
        String title = templateService.render(template.titleTemplate(), renderData);
        String body  = templateService.render(template.bodyTemplate(),  renderData);

        String collapseKey = persisted.getType().name() + "_" + recipientId;

        log.info("[FCM] NEW_DEVICE_LOGIN → root device: recipientId={}, rootDeviceId={}, fcmDevice={}, platform={}",
                recipientId, rootDeviceId, rootDevice.getId(), rootDevice.getPlatform());

        sendToDevice(rootDevice, title, body, collapseKey,
                recipientId, null, null, null, 0, 0,
                persisted.getType().name(), null, persisted.getPayload());
    }

    private String getStr(Notification n, String key) {
        if (n.getPayload() == null) return null;
        Object v = n.getPayload().get(key);
        return v != null ? v.toString() : null;
    }
}
