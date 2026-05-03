package com.bondhub.notificationservices.service.delivery;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;
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

        var prefs = userPreferenceService.getPreferences(event.getRecipientId());

        if (!userPreferenceService.allow(prefs, null, event.getType())) {
            log.info("Notification disabled by user preference: recipientId={}, type={}",
                    event.getRecipientId(), event.getType());
            return;
        }

        boolean silencedByDnd = userPreferenceService.shouldSilenceByDnd(
                event.getRecipientId(),
                prefs,
                null
        );

        Notification target;

        if (isTransientType(event.getType())) {
            target = persistenceService.buildTransient(event);
        } else {
            target = persistenceService.persist(event);
        }

        if (target == null) {
            log.warn("Target notification creation failed - skipping strategies: recipientId={}",
                    event.getRecipientId());
            return;
        }

        if (silencedByDnd) {
            log.info("Notification silenced by DND: recipientId={}, type={}",
                    event.getRecipientId(), event.getType());

            dndMissedNotificationService.record(event);

            if (event.getType() == NotificationType.MESSAGE_DIRECT) {
                autoReplyService.replyIfNeeded(event);
            }

            return;
        }

        for (NotificationStrategy strategy : strategies) {
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
