package com.bondhub.notificationservices.service.notification;

import com.bondhub.notificationservices.dto.request.notification.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.event.RawNotificationEvent;
import com.bondhub.notificationservices.pipeline.NotificationBatcherStep;
import com.bondhub.notificationservices.pipeline.UserPreferenceCheckerStep;
import com.bondhub.notificationservices.pipeline.UserValidatorStep;
import com.bondhub.notificationservices.service.delivery.DeliveryService;
import com.bondhub.notificationservices.service.notification.assembler.NotificationAssemblerResolver;
import com.bondhub.notificationservices.service.preference.UserPreferenceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationServiceImpl implements NotificationService {

    NotificationAssemblerResolver assemblerResolver;
    UserValidatorStep userValidatorStep;
    NotificationBatcherStep notificationBatcherStep;
    UserPreferenceCheckerStep userPreferenceCheckerStep;
    DeliveryService deliveryService;
    UserPreferenceService userPreferenceService;

    @Override
    public void createFriendRequestNotification(CreateFriendRequestNotificationRequest request) {
        process(NotificationType.FRIEND_REQUEST, request);
    }

    private void process(NotificationType type, Object request) {
        RawNotificationEvent event = assemblerResolver.get(type).build(request);
        log.info("Processing notification: type={}, recipient={}", type, event.getRecipientId());

        if (!userValidatorStep.process(event)) return;
        if (!notificationBatcherStep.process(event)) return;
        if (!userPreferenceCheckerStep.process(event)) return;

        deliveryService.deliver(toImmediate(event));
    }

    private BatchedNotificationEvent toImmediate(RawNotificationEvent event) {
        String locale = userPreferenceService.getLocale(event.getRecipientId());

        Map<String, Object> payload = new HashMap<>(
                event.getPayload() != null ? event.getPayload() : Collections.emptyMap()
        );
        payload.put("actorId",     event.getActorId());
        payload.put("referenceId", event.getReferenceId());
        payload.put("occurredAt",  event.getOccurredAt() != null ? event.getOccurredAt().toString() : null);

        return BatchedNotificationEvent.builder()
                .recipientId(event.getRecipientId())
                .type(event.getType())
                .actorIds(List.of(event.getActorId()))
                .actorCount(1)
                .firstActorId(event.getActorId())
                .firstActorName(event.getActorName())
                .firstActorAvatar(event.getActorAvatar())
                .othersCount(0)
                .locale(locale)
                .rawPayloads(List.of(payload))
                .batchedAt(LocalDateTime.now())
                .build();
    }
}
