package com.bondhub.notificationservices.service.delivery;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.service.delivery.strategy.InAppDeliveryStrategy;
import com.bondhub.notificationservices.service.delivery.strategy.NotificationStrategy;
import com.bondhub.notificationservices.service.dnd.AutoReplyService;
import com.bondhub.notificationservices.service.dnd.DndMissedNotificationService;
import com.bondhub.notificationservices.service.persistence.NotificationPersistenceService;
import com.bondhub.notificationservices.service.user.preference.UserPreferenceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeliveryServiceImpl implements DeliveryService {

    NotificationPersistenceService persistenceService;
    UserPreferenceService userPreferenceService;
    DndMissedNotificationService dndMissedNotificationService;
    AutoReplyService autoReplyService;
    List<NotificationStrategy> strategies;

    @Override
    public void deliver(BatchedNotificationEvent event) {
        log.info("Processing delivery: type={}, recipientId={}",
                event.getType(), event.getRecipientId());

        // Step 1: Check notification type allowed (local UserDevice first, Feign fallback)
        if (!userPreferenceService.allow(event.getRecipientId(), event.getType())) {
            log.info("Notification disabled by user preference: recipientId={}, type={}",
                    event.getRecipientId(), event.getType());
            return;
        }

        // Step 2: Check DND (purely local, no Feign needed)
        boolean silencedByDnd = userPreferenceService.shouldSilenceByDnd(event.getRecipientId());

        // Step 3: Persist or build transient notification
        Notification target;

        if (isTransientType(event.getType())) {
            target = persistenceService.buildTransient(event);
        } else {
            target = persistenceService.persist(event);
        }

        persistenceService.updateUnreadState(event.getRecipientId(), event.getLastActorId(), event.getType(), event.getReferenceId());

        if (target == null) {
            log.warn("Target notification creation failed - skipping strategies: recipientId={}",
                    event.getRecipientId());
            return;
        }

        // Step 4: Execute InAppDeliveryStrategy regardless of DND so the sidebar updates in real-time
        strategies.stream()
                .filter(s -> s instanceof InAppDeliveryStrategy)
                .findFirst()
                .ifPresent(strategy -> {
                    try {
                        strategy.execute(target);
                    } catch (Exception e) {
                        log.error("InAppDeliveryStrategy failed: {}", e.getMessage(), e);
                    }
                });

        // Step 5: If DND active, record as missed and optionally auto-reply
        if (silencedByDnd) {
            log.info("Notification silenced by DND: recipientId={}, type={}",
                    event.getRecipientId(), event.getType());

            dndMissedNotificationService.record(event);

            if (event.getType() == NotificationType.MESSAGE_DIRECT) {
                autoReplyService.replyIfNeeded(event);
            }

            return;
        }

        // Step 6: Execute other delivery strategies (FCM, Email)
        for (NotificationStrategy strategy : strategies) {
            if (strategy instanceof InAppDeliveryStrategy) {
                continue; // Already executed
            }
            try {
                strategy.execute(target);
            } catch (Exception e) {
                log.error("Strategy {} failed: {}",
                        strategy.getClass().getSimpleName(),
                        e.getMessage(),
                        e
                );
            }
        }
    }

    private boolean isTransientType(NotificationType type) {
        return type == NotificationType.MESSAGE_DIRECT
                || type == NotificationType.MESSAGE_GROUP
                || type == NotificationType.CALL
                || type == NotificationType.SYSTEM;
    }
}
