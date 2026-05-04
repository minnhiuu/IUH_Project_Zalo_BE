package com.bondhub.notificationservices.service.delivery;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.SystemNotificationEvent;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.enums.Platform;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.model.UserDevice;
import com.bondhub.notificationservices.model.UserNotificationState;
import com.bondhub.notificationservices.repository.NotificationRepository;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.bondhub.notificationservices.service.template.NotificationTemplateService;
import com.bondhub.notificationservices.service.user.preference.UserPreferenceService;
import com.bondhub.notificationservices.dto.response.template.NotificationTemplateResponse;
import com.google.firebase.messaging.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SystemNotificationDeliveryServiceImpl implements SystemNotificationDeliveryService {

    static final RetryTemplate FCM_RETRY = RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(500, 2, 4000)
            .retryOn(RuntimeException.class)
            .build();

    NotificationRepository notificationRepository;
    UserDeviceRepository userDeviceRepository;
    NotificationTemplateService templateService;
    UserPreferenceService userPreferenceService;
    MongoTemplate mongoTemplate;

    @NonFinal
    @Value("${bondhub.frontend-url}")
    String frontendUrl;

    @Override
    public void deliver(SystemNotificationEvent event) {
        log.info("[SystemDelivery] Processing: type={}, category={}, recipient={}",
                event.getType(), event.getCategory(), event.getRecipientId());

        Notification persisted = persist(event);
        if (persisted == null) {
            log.warn("[SystemDelivery] Persistence failed, skipping delivery: recipient={}", event.getRecipientId());
            return;
        }

        if (!userPreferenceService.allow(event.getRecipientId(), event.getType())) {
            log.info("[SystemDelivery] Filtered by user preference: recipient={}, type={}",
                    event.getRecipientId(), event.getType());
            return;
        }

        sendFcm(persisted, event);
    }

    private Notification persist(SystemNotificationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        if (event.getMetadata() != null) {
            payload.putAll(event.getMetadata());
        }
        payload.put("category", event.getCategory().name());
        if (event.getActorId() != null) {
            payload.put("actorId", event.getActorId());
        }

        Notification notification = Notification.builder()
                .userId(event.getRecipientId())
                .type(event.getType())
                .referenceId(event.getReferenceId())
                .actorIds(event.getActorId() != null ? List.of(event.getActorId()) : List.of())
                .payload(payload)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);

        mongoTemplate.upsert(
                new Query(Criteria.where("userId").is(event.getRecipientId())),
                new Update().inc("unreadCount", 1L),
                UserNotificationState.class
        );

        log.debug("[SystemDelivery] Persisted notification: id={}, type={}, recipient={}",
                saved.getId(), event.getType(), event.getRecipientId());
        return saved;
    }

    private void sendFcm(Notification persisted, SystemNotificationEvent event) {
        String recipientId = persisted.getUserId();

        List<UserDevice> devices = userDeviceRepository.findByUserId(recipientId);
        if (devices.isEmpty()) {
            log.debug("[SystemDelivery] FCM skip: no devices for recipient={}", recipientId);
            return;
        }

        String globalLocale = userPreferenceService.getLocale(recipientId);

        Map<String, Object> renderData = new HashMap<>();
        if (event.getMetadata() != null) {
            renderData.putAll(event.getMetadata());
        }

        String collapseKey = persisted.getType().name() + "_" + recipientId;

        Map<String, NotificationTemplateResponse> templateCache = new HashMap<>();

        for (UserDevice device : devices) {
            String deviceLocale = device.getLocale() != null ? device.getLocale() : globalLocale;

            if (!isAllowedOnDevice(device, persisted.getType())) {
                log.debug("[SystemDelivery] FCM skip: filtered by preference: recipient={}, device={}",
                        recipientId, device.getDeviceId());
                continue;
            }

            String title;
            String body;

            if (event.getTitle() != null && event.getBody() != null) {
                title = event.getTitle();
                body = event.getBody();
            } else {
                var template = templateCache.computeIfAbsent(deviceLocale, loc ->
                        templateService.getTemplate(persisted.getType(), NotificationChannel.FCM, loc));
                title = templateService.render(template.titleTemplate(), renderData);
                body = templateService.render(template.bodyTemplate(), renderData);
            }

            if ("".equals(title) && "".equals(body)) {
                log.warn("[SystemDelivery] FCM skip: empty title and body for device={}, type={}",
                        device.getId(), persisted.getType());
                continue;
            }

            sendToDevice(device, title, body, collapseKey, recipientId, persisted.getType().name(),
                    event.getMetadata());
        }
    }

    private void sendToDevice(UserDevice device, String title, String body, String collapseKey,
                              String recipientId, String type, Map<String, Object> metadata) {
        String url = frontendUrl;
        if (!url.endsWith("/")) url += "/";

        Map<String, String> dataPayload = new HashMap<>();
        dataPayload.put("type", type);
        dataPayload.put("title", title != null ? title : "");
        dataPayload.put("body", body != null ? body : "");
        dataPayload.put("url", url);

        if (metadata != null) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (!dataPayload.containsKey(entry.getKey()) && entry.getValue() != null) {
                    dataPayload.put(entry.getKey(), entry.getValue().toString());
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
            messageBuilder.setWebpushConfig(WebpushConfig.builder()
                    .setFcmOptions(WebpushFcmOptions.withLink(url))
                    .build());
        }

        if (device.getPlatform() == Platform.ANDROID) {
            messageBuilder.setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setCollapseKey(collapseKey)
                    .build());
        }

        if (device.getPlatform() == Platform.IOS) {
            messageBuilder.setApnsConfig(ApnsConfig.builder()
                    .setAps(Aps.builder()
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
                        log.warn("[SystemDelivery] FCM stale token, removing device: recipient={}, device={}, code={}",
                                recipientId, device.getId(), code);
                        userDeviceRepository.delete(device);
                        return null;
                    }
                    log.warn("[SystemDelivery] FCM transient error [attempt {}]: recipient={}, device={}, error={}",
                            ctx.getRetryCount() + 1, recipientId, device.getId(), e.getMessage());
                    throw new RuntimeException("FCM transient error: " + e.getMessage(), e);
                }
            });
            if (messageId != null) {
                log.info("[SystemDelivery] FCM sent: recipient={}, device={}, messageId={}",
                        recipientId, device.getId(), messageId);
            }
        } catch (Exception e) {
            log.error("[SystemDelivery] FCM failed after retries: recipient={}, device={}, error={}",
                    recipientId, device.getId(), e.getMessage());
        }
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
}
