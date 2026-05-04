package com.bondhub.notificationservices.service.delivery.strategy;

import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.dto.response.notification.NotificationResponse;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.mapper.NotificationMapper;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.model.UserDevice;
import com.bondhub.notificationservices.publisher.SocketEventPublisher;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.bondhub.notificationservices.service.delivery.NotificationStrategyHelper;
import com.bondhub.notificationservices.service.user.preference.UserPreferenceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InAppDeliveryStrategy implements NotificationStrategy {

    SocketEventPublisher socketEventPublisher;
    NotificationStrategyHelper strategyHelper;
    NotificationMapper notificationMapper;
    UserDeviceRepository userDeviceRepository;
    UserPreferenceService userPreferenceService;

    @Override
    public void execute(Notification persisted) {
        // MESSAGE_DIRECT and CALL have their own dedicated WebSocket channels
        // (/queue/messages, /queue/call-signals). Skip to avoid duplicate realtime notifications.
        if (persisted.getType() == NotificationType.MESSAGE_DIRECT ||
            persisted.getType() == NotificationType.MESSAGE_GROUP ||
            persisted.getType() == NotificationType.CALL) {
            log.debug("[InApp] Skipping realtime for direct-channel type: {}", persisted.getType());
            return;
        }

        String recipientId = persisted.getUserId();
        log.info("[InApp] Preparing real-time signal for notification: {} for user: {}", persisted.getId(), recipientId);

        try {
            // 1. Get unique locales across all user devices
            List<UserDevice> devices = userDeviceRepository.findByUserId(recipientId);
            List<String> locales = devices.stream()
                    .map(d -> d.getLocale() != null ? d.getLocale() : "vi")
                    .distinct()
                    .collect(Collectors.toList());
            
            // Ensure at least the user's global preferred locale is included
            String globalLocale = userPreferenceService.getLocale(recipientId);
            if (!locales.contains(globalLocale)) locales.add(globalLocale);

            // 2. Render for each locale and build translations map
            Map<String, NotificationResponse.LocalizedContent> translations = new HashMap<>();
            for (String loc : locales) {
                var rendered = strategyHelper.render(persisted, NotificationChannel.IN_APP, loc);
                translations.put(loc, NotificationResponse.LocalizedContent.builder()
                        .title(rendered.title())
                        .body(rendered.body())
                        .build());
            }

            // 3. Map to Response DTO with translations
            var defaultRender = translations.getOrDefault(globalLocale, translations.values().iterator().next());
            
            NotificationResponse response = NotificationResponse.builder()
                    .id(persisted.getId())
                    .type(persisted.getType())
                    .referenceId(persisted.getReferenceId())
                    .title(defaultRender.title())
                    .body(defaultRender.body())
                    .translations(translations)
                    .actorIds(persisted.getActorIds())
                    .actorCount(persisted.getActorIds() != null ? persisted.getActorIds().size() : 0)
                    .read(persisted.isRead())
                    .lastModifiedAt(persisted.getLastModifiedAt())
                    .payload(persisted.getPayload())
                    .build();

            // 4. Create Socket Event
            SocketEvent socketEvent = new SocketEvent(
                    SocketEventType.NOTIFICATION,
                    recipientId,
                    "/queue/notifications",
                    response
            );

            // 5. Publish to Kafka
            socketEventPublisher.publish(socketEvent);

            log.info("[InApp] Real-time signal sent for notification: {} to user: {}", 
                    persisted.getId(), recipientId);
            
        } catch (Exception e) {
            log.error("[InApp] Failed to deliver real-time notification to user {}: {}", recipientId, e.getMessage());
        }
    }
}
