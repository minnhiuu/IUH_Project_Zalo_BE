package com.bondhub.notificationservices.service.delivery.handler;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.repository.NotificationRepository;
import com.bondhub.notificationservices.service.notificationtemplate.NotificationTemplateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InAppDeliveryHandler implements DeliveryHandler {

    NotificationRepository notificationRepository;
    NotificationTemplateService templateService;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void deliver(BatchedNotificationEvent event) {
        Map<String, Object> data = buildTemplateData(event);

        String title = renderSafe(event, data, "title");
        String body  = renderSafe(event, data, "body");

        Notification notification = Notification.builder()
                .userId(event.getRecipientId())
                .type(event.getType())
                .referenceId(resolveReferenceId(event))
                .title(title)
                .body(body)
                .data(buildData(event))
                .isRead(false)
                .build();

        notificationRepository.save(notification);
        log.info("IN_APP saved: recipientId={}, type={}", event.getRecipientId(), event.getType());

        // TODO: push realtime to client via WebSocket (if online)
    }

    private Map<String, Object> buildTemplateData(BatchedNotificationEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("firstName",   event.getFirstActorName() != null ? event.getFirstActorName() : event.getFirstActorId());
        data.put("othersCount", event.getOthersCount());
        data.put("count",       event.getActorCount());
        return data;
    }

    private Map<String, Object> buildData(BatchedNotificationEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("actorId",     event.getFirstActorId());
        data.put("actorName",   event.getFirstActorName());
        data.put("actorAvatar", event.getFirstActorAvatar());
        data.put("actorCount",  event.getActorCount());
        data.put("othersCount", event.getOthersCount());
        return data;
    }

    private String renderSafe(BatchedNotificationEvent event, Map<String, Object> data, String field) {
        try {
            return "title".equals(field)
                    ? templateService.renderTitle(event.getType(), NotificationChannel.IN_APP, event.getLocale(), data)
                    : templateService.renderBody(event.getType(), NotificationChannel.IN_APP, event.getLocale(), data);
        } catch (Exception e) {
            log.warn("IN_APP template not found for type={} locale={}, using fallback", event.getType(), event.getLocale());
            return "title".equals(field) ? event.getActorCount() + " new notifications" : "";
        }
    }

    private String resolveReferenceId(BatchedNotificationEvent event) {
        if (event.getRawPayloads() != null && !event.getRawPayloads().isEmpty()) {
            Object ref = event.getRawPayloads().getFirst().get("referenceId");
            if (ref != null) return ref.toString();
        }
        return null;
    }
}
