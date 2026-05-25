package com.bondhub.notificationservices.service.dnd;

import com.bondhub.notificationservices.enums.DndMissedStatus;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.DndMissedNotification;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.repository.DndMissedNotificationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DndMissedNotificationServiceImpl implements DndMissedNotificationService {

    DndMissedNotificationRepository repository;
    DndSummaryGroupResolver groupResolver;

    @Override
    public void record(Notification notification) {
        Map<String, Object> payload = notification.getPayload() != null
                ? notification.getPayload()
                : Map.of();

        DndMissedNotification missed = DndMissedNotification.builder()
                .userId(notification.getUserId())
                .type(notification.getType())
                .referenceId(notification.getReferenceId())
                .conversationId(getString(payload, "conversationId"))
                .postId(getString(payload, "postId"))
                .actorId(getString(payload, "actorId"))
                .actorName(getString(payload, "actorName"))
                .groupName(getString(payload, "groupName"))
                .summaryGroupKey(groupResolver.resolve(notification))
                .payload(new HashMap<>(payload))
                .status(DndMissedStatus.PENDING)
                .occurredAt(LocalDateTime.now())
                .build();

        repository.save(missed);
    }

    @Override
    public void record(BatchedNotificationEvent event) {
        Map<String, Object> payload = extractLastPayload(event);

        DndMissedNotification missed = DndMissedNotification.builder()
                .userId(event.getRecipientId())
                .type(event.getType())
                .referenceId(event.getReferenceId())
                .conversationId(getString(payload, "conversationId"))
                .postId(getString(payload, "postId"))
                .actorId(event.getLastActorId())
                .actorName(event.getLastActorName())
                .groupName(getString(payload, "groupName"))
                .summaryGroupKey(groupResolver.resolve(event))
                .payload(payload)
                .status(DndMissedStatus.PENDING)
                .occurredAt(
                        event.getLastOccurredAt() != null
                                ? event.getLastOccurredAt()
                                : LocalDateTime.now()
                )
                .build();

        repository.save(missed);
    }

    private Map<String, Object> extractLastPayload(BatchedNotificationEvent event) {
        if (event.getRawPayloads() == null || event.getRawPayloads().isEmpty()) {
            return new HashMap<>();
        }

        return new HashMap<>(event.getRawPayloads().get(event.getRawPayloads().size() - 1));
    }

    private String getString(Map<String, Object> payload, String key) {
        if (payload == null) return null;

        Object value = payload.get(key);

        return value != null ? value.toString() : null;
    }
}
