package com.bondhub.notificationservices.service.delivery;

import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.service.delivery.handler.FcmDeliveryHandler;
import com.bondhub.notificationservices.service.delivery.handler.InAppDeliveryHandler;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeliveryServiceImpl implements DeliveryService {

    InAppDeliveryHandler inAppHandler;
    FcmDeliveryHandler fcmHandler;

    @Override
    public void deliver(BatchedNotificationEvent event) {
        log.info("Delivering: type={}, recipientId={}, batchActors={}",
                event.getType(), event.getRecipientId(), event.getActorCount());

        Notification persisted = inAppHandler.persistAndReturn(event);
        if (persisted == null) {
            log.warn("Persist returned null — skipping FCM: type={}, recipientId={}",
                    event.getType(), event.getRecipientId());
            return;
        }

        // TODO: if user is online, push via WebSocket for real-time in-app display instead of FCM
        try {
            fcmHandler.push(persisted);
        } catch (Exception e) {
            log.error("FCM push failed: recipientId={}, type={}, error={}",
                    event.getRecipientId(), event.getType(), e.getMessage(), e);
        }
    }
}
