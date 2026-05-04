package com.bondhub.notificationservices.service.push;

import com.bondhub.notificationservices.enums.DeliveryStatus;
import com.bondhub.notificationservices.enums.Platform;
import com.bondhub.notificationservices.model.UserDevice;
import com.bondhub.notificationservices.repository.NotificationRepository;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.google.firebase.messaging.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Recover;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FcmServiceImpl implements FcmService {

    UserDeviceRepository userDeviceRepository;
    NotificationRepository notificationRepository;

    @NonFinal
    @Value("${bondhub.frontend-url}")
    String frontendUrl;

    @Override
    @Retryable(
        value = { RuntimeException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendPush(String notificationId, UserDevice device, String title, String body, String type, Map<String, Object> metadata) {
        String url = frontendUrl;
        if (!url.endsWith("/")) url += "/";
        String clickUrl = url + "?noti_open=true";

        Map<String, String> dataPayload = new HashMap<>();
        dataPayload.put("type", type);
        dataPayload.put("title", title != null ? title : "");
        dataPayload.put("body", body != null ? body : "");
        dataPayload.put("url", clickUrl);

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
        
        // Determine the logical Grouping ID (Collapse Key / Tag)
        String groupingId = notificationId;
        if (type != null && (type.equals("MESSAGE_DIRECT") || type.equals("MESSAGE_GROUP"))) {
            String conversationId = (String) metadata.get("conversationId");
            if (conversationId != null) {
                groupingId = "CHAT_" + conversationId;
            }
        }
        if (groupingId == null) {
            groupingId = UUID.randomUUID().toString();
        }

        Message.Builder messageBuilder = Message.builder()
                .setToken(device.getFcmToken())
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(dataPayload);

        if (device.getPlatform() == Platform.WEB) {
            messageBuilder.setWebpushConfig(WebpushConfig.builder()
                    .setFcmOptions(WebpushFcmOptions.withLink(clickUrl))
                    .putHeader("Topic", groupingId)
                    .setNotification(WebpushNotification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .setIcon("http://localhost:5173/images/logo.jpg") // <--- Logo từ Frontend của bạn
                            .setTag(groupingId)
                            .build())
                    .build());
        }

        if (device.getPlatform() == Platform.ANDROID) {
            messageBuilder.setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setCollapseKey(groupingId)
                    .build());
        }

        if (device.getPlatform() == Platform.IOS) {
            messageBuilder.setApnsConfig(ApnsConfig.builder()
                    .putHeader("apns-collapse-id", groupingId)
                    .setAps(Aps.builder()
                            .setContentAvailable(true)
                            .setMutableContent(true)
                            .setSound("default")
                            .build())
                    .build());
        }

        try {
            FirebaseMessaging.getInstance().send(messageBuilder.build());
            log.info("[FCM] Sent push to device: {} (type={})", device.getDeviceId(), type);
            
            // Mark as SENT if not already (only if it's a valid ObjectId string)
            if (notificationId != null && notificationId.matches("^[0-9a-fA-F]{24}$")) {
                notificationRepository.findById(notificationId).ifPresent(n -> {
                    if (n.getDeliveryStatus() != DeliveryStatus.SENT) {
                        n.setDeliveryStatus(DeliveryStatus.SENT);
                        notificationRepository.save(n);
                    }
                });
            }
        } catch (FirebaseMessagingException e) {
            log.error("[FCM] Push failed for device {}: {}", device.getDeviceId(), e.getMessage());
            
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                log.warn("[FCM] Stale token detected, removing device: {}", device.getDeviceId());
                userDeviceRepository.delete(device);
                return; // No retry for unregistered tokens
            }
            
            // For other errors, throw to trigger @Retryable
            throw new RuntimeException("FCM delivery failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[FCM] Unexpected error for device {}: {}", device.getDeviceId(), e.getMessage());
            throw new RuntimeException("Unexpected FCM error", e);
        }
    }

    @Recover
    public void recover(RuntimeException e, String notificationId, UserDevice device, String title, String body, String type, Map<String, Object> metadata) {
        log.error("[FCM-RECOVER] All retries failed for device {}. Final error: {}", device.getDeviceId(), e.getMessage());
        
        if (notificationId != null && notificationId.matches("^[0-9a-fA-F]{24}$")) {
            notificationRepository.findById(notificationId).ifPresent(n -> {
                if (n.getDeliveryStatus() == DeliveryStatus.PENDING) {
                    n.setDeliveryStatus(DeliveryStatus.FAILED);
                    notificationRepository.save(n);
                }
            });
        }
    }
}
