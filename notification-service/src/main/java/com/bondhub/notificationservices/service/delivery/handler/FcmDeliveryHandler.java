package com.bondhub.notificationservices.service.delivery.handler;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.UserDevice;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.bondhub.notificationservices.service.notificationtemplate.NotificationTemplateService;
import com.bondhub.notificationservices.service.presence.PresenceService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;
import org.springframework.retry.support.RetryTemplate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FcmDeliveryHandler implements DeliveryHandler {

    static final RetryTemplate FCM_RETRY = RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(500, 2, 4000)
            .retryOn(RuntimeException.class)
            .build();

    UserDeviceRepository userDeviceRepository;
    NotificationTemplateService templateService;
    PresenceService presenceService;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.FCM;
    }

    @Override
    public void deliver(BatchedNotificationEvent event) {
        if (presenceService.isOnline(event.getRecipientId())) {
            log.debug("FCM skip: user is online, recipientId={}", event.getRecipientId());
            return;
        }

        List<UserDevice> devices = userDeviceRepository.findByUserId(event.getRecipientId());

        if (devices.isEmpty()) {
            log.debug("FCM skip: no devices for recipientId={}", event.getRecipientId());
            return;
        }

        Map<String, Object> data = buildTemplateData(event);

        String title = renderSafe(event, data, "title");
        String body  = renderSafe(event, data, "body");

        Notification fcmNotification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        for (UserDevice device : devices) {
            sendToDevice(device, fcmNotification, event);
        }
    }

    private void sendToDevice(UserDevice device, Notification fcmNotification, BatchedNotificationEvent event) {
        String collapseKey = event.getType().name() + "_" + event.getRecipientId();

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
                                .setIcon(event.getFirstActorAvatar() != null ? event.getFirstActorAvatar() : "/images/logo.png")
                                .setBadge("/images/logo.png")
                                .setTag(collapseKey) 
                                .build())
                        .build())
                .putData("type",        event.getType().name())
                .putData("actorId",     event.getFirstActorId())
                .putData("actorName",   event.getFirstActorName()   != null ? event.getFirstActorName()   : "")
                .putData("actorAvatar", event.getFirstActorAvatar() != null ? event.getFirstActorAvatar() : "")
                .putData("actorCount",  String.valueOf(event.getActorCount()))
                .build();

        try {
            String messageId = FCM_RETRY.execute(ctx -> {
                try {
                    return FirebaseMessaging.getInstance().send(message);
                } catch (FirebaseMessagingException e) {
                    MessagingErrorCode code = e.getMessagingErrorCode();
                    if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                        log.warn("FCM stale token, removing device: recipientId={}, deviceId={}, code={}",
                                event.getRecipientId(), device.getId(), code);
                        userDeviceRepository.delete(device);
                        return null; 
                    }
                    log.warn("FCM transient error [attempt {}]: recipientId={}, deviceId={}, error={}",
                            ctx.getRetryCount() + 1, event.getRecipientId(), device.getId(), e.getMessage());
                    throw new RuntimeException("FCM transient error: " + e.getMessage(), e);
                }
            });
            if (messageId != null) {
                log.info("FCM sent: recipientId={}, deviceId={}, messageId={}", event.getRecipientId(), device.getId(), messageId);
            }
        } catch (Exception e) {
            log.error("FCM failed after max retries: recipientId={}, deviceId={}, error={}",
                    event.getRecipientId(), device.getId(), e.getMessage());
        }
    }

    private Map<String, Object> buildTemplateData(BatchedNotificationEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("firstName",   event.getFirstActorName() != null ? event.getFirstActorName() : event.getFirstActorId());
        data.put("othersCount", event.getOthersCount());
        data.put("count",       event.getActorCount());
        return data;
    }

    private String renderSafe(BatchedNotificationEvent event, Map<String, Object> data, String field) {
        try {
            return "title".equals(field)
                    ? templateService.renderTitle(event.getType(), NotificationChannel.FCM, event.getLocale(), data)
                    : templateService.renderBody(event.getType(), NotificationChannel.FCM, event.getLocale(), data);
        } catch (Exception e) {
            log.warn("FCM template not found for type={} locale={}, using fallback", event.getType(), event.getLocale());
            return "title".equals(field) ? event.getActorCount() + " new notifications" : "";
        }
    }
}
