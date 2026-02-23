package com.bondhub.notificationservices.service.delivery;

import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.service.delivery.handler.DeliveryHandler;
import com.bondhub.notificationservices.service.delivery.handler.DeliveryHandlerFactory;
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

    DeliveryHandlerFactory handlerFactory;

    @Override
    public void deliver(BatchedNotificationEvent event) {
        log.info("Delivering: type={}, recipientId={}, actorCount={}",
                event.getType(), event.getRecipientId(), event.getActorCount());

        for (DeliveryHandler handler : handlerFactory.getAll()) {
            try {
                handler.deliver(event);
            } catch (Exception e) {
                log.error("Delivery failed: channel={}, recipientId={}, error={}",
                        handler.getChannel(), event.getRecipientId(), e.getMessage(), e);
            }
        }
    }
}
