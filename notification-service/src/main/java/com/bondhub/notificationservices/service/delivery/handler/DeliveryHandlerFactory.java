package com.bondhub.notificationservices.service.delivery.handler;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DeliveryHandlerFactory {

    private final Map<NotificationChannel, DeliveryHandler> handlers;

    public DeliveryHandlerFactory(List<DeliveryHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(DeliveryHandler::getChannel, Function.identity()));
        log.info("Registered delivery handlers: {}", handlers.keySet());
    }

    public DeliveryHandler get(NotificationChannel channel) {
        return handlers.get(channel);
    }

    public List<DeliveryHandler> getAll() {
        return List.copyOf(handlers.values());
    }
}
