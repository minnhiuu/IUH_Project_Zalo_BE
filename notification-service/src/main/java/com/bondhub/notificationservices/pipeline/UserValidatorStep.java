package com.bondhub.notificationservices.pipeline;

import com.bondhub.notificationservices.client.UserServiceClient;
import com.bondhub.common.event.notification.RawNotificationEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserValidatorStep implements PipelineStep {

    UserServiceClient userServiceClient;


    @Override
    public boolean process(RawNotificationEvent event) {
        if (event.getRecipientId() == null || event.getRecipientId().isBlank()) {
            log.warn("Drop event: missing recipientId, type={}", event.getType());
            return false;
        }

         if(!userServiceClient.existsById(event.getRecipientId()).hasBody()) {
             log.warn("Drop event: recipient not found, type={}", event.getType());
             return false;
         }

        return true;
    }
}
