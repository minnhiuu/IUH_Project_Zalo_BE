package com.bondhub.notificationservices.service.delivery.handler;

import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.model.UserDevice;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.bondhub.notificationservices.service.notificationtemplate.NotificationTemplateService;
import com.bondhub.notificationservices.service.presence.PresenceService;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FcmDeliveryHandler {

    static final RetryTemplate FCM_RETRY = RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(500, 2, 4000)
            .retryOn(RuntimeException.class)
            .build();

    UserDeviceRepository userDeviceRepository;
    NotificationTemplateService templateService;
    PresenceService presenceService;

    public void push(Notification persisted) {
        String recipientId = persisted.getUserId();

        if (presenceService.isOnline(recipientId)) {
            log.debug("FCM skip: user is online, recipientId={}", recipientId);
            return;
        }

        List<UserDevice> devices = userDeviceRepository.findByUserId(recipientId);
        if (devices.isEmpty()) {
            log.debug("FCM skip: no devices for recipientId={}", recipientId);
            return;
        }

        String title = persisted.getTitle();
        String body = persisted.getBody();
        int actorCount = persisted.getActorIds() != null ? persisted.getActorIds().size() : 0;
        int othersCount = Math.max(0, actorCount - 1);

        String lastActorId = getStr(persisted, "actorId");
        String lastActorName = getStr(persisted, "actorName");
        String lastActorAvatar = getStr(persisted, "actorAvatar");

        com.google.firebase.messaging.Notification fcmNotification =
                com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build();

        String collapseKey = persisted.getType().name() + "_" + recipientId;

        for (UserDevice device : devices) {
            sendToDevice(device, fcmNotification, collapseKey,
                    recipientId, lastActorId, lastActorName, lastActorAvatar,
                    actorCount, othersCount, persisted.getType().name());
        }
    }

    private void sendToDevice(UserDevice device,
                              com.google.firebase.messaging.Notification fcmNotification,
                              String collapseKey,
                              String recipientId,
                              String lastActorId,
                              String lastActorName,
                              String lastActorAvatar,
                              int actorCount,
                              int othersCount,
                              String type) {

        Message message = Message.builder()
                .setToken(device.getFcmToken())
                .setNotification(fcmNotification)
                .setAndroidConfig(AndroidConfig.builder()
                        .setCollapseKey(collapseKey)
                        .setNotification(AndroidNotification.builder()
                                .setTag(collapseKey)
                                .build())
                        .build())
                .setWebpushConfig(WebpushConfig.builder()
                        .setNotification(WebpushNotification.builder()
                                .setIcon(lastActorAvatar != null ? lastActorAvatar : "/images/logo.png")
                                .setBadge("/images/logo.png")
                                .setTag(collapseKey)
                                .build())
                        .build())
                .putData("type", type)
                .putData("actorId", lastActorId != null ? lastActorId : "")
                .putData("actorName", lastActorName != null ? lastActorName : "")
                .putData("actorAvatar", lastActorAvatar != null ? lastActorAvatar : "")
                .putData("actorCount", String.valueOf(actorCount))
                .putData("othersCount", String.valueOf(othersCount))
                .build();

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

    private String getStr(Notification n, String key) {
        if (n.getData() == null) return null;
        Object v = n.getData().get(key);
        return v != null ? v.toString() : null;
    }
}
