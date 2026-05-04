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
import com.bondhub.notificationservices.service.push.FcmService;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SystemNotificationDeliveryServiceImpl implements SystemNotificationDeliveryService {

    NotificationRepository notificationRepository;
    UserDeviceRepository userDeviceRepository;
    NotificationTemplateService templateService;
    UserPreferenceService userPreferenceService;
    NotificationStrategyHelper strategyHelper;
    FcmService fcmService;
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

            Map<String, Object> metadata = new HashMap<>();
            if (event.getMetadata() != null) {
                metadata.putAll(event.getMetadata());
            }

            fcmService.sendPush(device, title, body, persisted.getType().name(), metadata);
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
