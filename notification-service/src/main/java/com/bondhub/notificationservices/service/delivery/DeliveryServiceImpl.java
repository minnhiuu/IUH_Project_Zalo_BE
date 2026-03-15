package com.bondhub.notificationservices.service.delivery;

import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.service.delivery.strategy.NotificationStrategy;
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
    List<NotificationStrategy> strategies;

    @Override
    public void deliver(BatchedNotificationEvent event) {
        log.info("Processing delivery: type={}, recipientId={}", event.getType(), event.getRecipientId());

        Notification persisted = persistenceService.persist(event);
        
        if (persisted == null) {
            log.warn("Persistence failed - skipping strategies: recipientId={}", event.getRecipientId());
            return;
        }

        var prefs = userPreferenceService.getPreferences(event.getRecipientId());
        if (!userPreferenceService.allow(prefs, null, event.getType())) {
            log.info("Notification filtered out by user preference: recipientId={}, type={}", 
                    event.getRecipientId(), event.getType());
            return;
        }

        for (NotificationStrategy strategy : strategies) {
            try {
                strategy.execute(persisted);
            } catch (Exception e) {
                log.error("Strategy {} failed: {}", strategy.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }
}
