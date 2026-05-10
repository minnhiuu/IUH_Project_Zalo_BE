package com.bondhub.notificationservices.service.dnd;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DndSummaryGroupResolver {

    public String resolve(Notification notification) {
        NotificationType type = notification.getType();
        Map<String, Object> payload = notification.getPayload();

        if (type == NotificationType.MESSAGE_DIRECT || type == NotificationType.MESSAGE_GROUP) {
            String conversationId = getString(payload, "conversationId");

            if (conversationId == null || conversationId.isBlank()) {
                conversationId = notification.getReferenceId();
            }

            return "MESSAGE:" + conversationId;
        }

        if (type == NotificationType.FRIEND_REQUEST) {
            return "FRIEND_REQUEST";
        }

        if (isPostRelated(type)) {
            String postId = getString(payload, "postId");

            if (postId == null || postId.isBlank()) {
                postId = notification.getReferenceId();
            }

            return "POST:" + postId;
        }

        return type.name();
    }

    public String resolve(BatchedNotificationEvent event) {
        NotificationType type = event.getType();

        if (type == NotificationType.MESSAGE_DIRECT || type == NotificationType.MESSAGE_GROUP) {
            String conversationId = extractFromEventPayload(event, "conversationId");

            if (conversationId == null || conversationId.isBlank()) {
                conversationId = event.getReferenceId();
            }

            return "MESSAGE:" + conversationId;
        }

        if (type == NotificationType.FRIEND_REQUEST) {
            return "FRIEND_REQUEST";
        }

        if (isPostRelated(type)) {
            String postId = extractFromEventPayload(event, "postId");

            if (postId == null || postId.isBlank()) {
                postId = event.getReferenceId();
            }

            return "POST:" + postId;
        }

        return type.name();
    }

    public boolean isPostRelated(NotificationType type) {
        return switch (type) {
            case POST_LIKE, POST_COMMENT, POST_PUBLISHED,
                 COMMENT_LIKE, COMMENT_REPLY,
                 POST_TAG, POST_MENTION, COMMENT_MENTION -> true;
            default -> false;
        };
    }

    private String extractFromEventPayload(BatchedNotificationEvent event, String key) {
        if (event.getRawPayloads() == null || event.getRawPayloads().isEmpty()) {
            return null;
        }

        Map<String, Object> lastPayload = event.getRawPayloads().get(event.getRawPayloads().size() - 1);
        return getString(lastPayload, key);
    }

    private String getString(Map<String, Object> payload, String key) {
        if (payload == null) return null;

        Object value = payload.get(key);

        return value != null ? value.toString() : null;
    }
}
