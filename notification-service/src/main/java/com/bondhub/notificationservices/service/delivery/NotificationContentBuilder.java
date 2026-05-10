package com.bondhub.notificationservices.service.delivery;

import com.bondhub.notificationservices.dto.response.template.NotificationTemplateResponse;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.service.template.NotificationTemplateService;
import com.bondhub.notificationservices.service.user.preference.UserPreferenceService;
import com.bondhub.notificationservices.service.delivery.renderer.ChatMessageRenderer;
import com.bondhub.notificationservices.service.delivery.renderer.SystemMessageRenderer;
import com.bondhub.notificationservices.service.delivery.renderer.DefaultTemplateRenderer;
import com.bondhub.common.enums.NotificationType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationContentBuilder {

    NotificationTemplateService templateService;
    UserPreferenceService userPreferenceService;
    ChatMessageRenderer chatMessageRenderer;
    SystemMessageRenderer systemMessageRenderer;
    DefaultTemplateRenderer defaultTemplateRenderer;

    @Builder
    public record RenderedContent(String title, String body, String locale) {}

    public RenderedContent render(Notification notification, NotificationChannel channel, String forceLocale) {
        String recipientId = notification.getUserId();
        
        // 1. Determine Locale
        String locale = forceLocale;
        if (locale == null) {
            locale = userPreferenceService.getLocale(recipientId);
        }
        if (locale == null) locale = "vi";

        // 2. Prepare Render Data
        Map<String, Object> renderData = prepareRenderData(notification);

        // 3. Dispatch to appropriate renderer based on notification type
        String title;
        String body;

        NotificationType type = notification.getType();
        if (type == NotificationType.MESSAGE_DIRECT || type == NotificationType.MESSAGE_GROUP) {
            // Chat message rendering
            title = renderChatTitle(notification);
            body = chatMessageRenderer.renderBody(notification, channel, locale, renderData);
            
        } else if (type == NotificationType.SYSTEM) {
            // System message rendering
            title = renderChatTitle(notification);
            body = systemMessageRenderer.renderBody(notification, channel, locale, renderData);
            
        } else {
            // Generic template rendering for other types
            title = renderGenericTitle(notification, locale, renderData);
            body = defaultTemplateRenderer.renderBody(notification, channel, locale, renderData);
        }

        return RenderedContent.builder()
                .title(title)
                .body(body)
                .locale(locale)
                .build();
    }

    public String renderChatTitle(Notification notification) {
        boolean isGroup = Boolean.TRUE.equals(notification.getPayload().get("isGroup"));
        if (isGroup || notification.getType() == NotificationType.SYSTEM) {
            String groupName = getStr(notification, "groupName");
            if (groupName != null && !groupName.isBlank()) return groupName;
            
            return notification.getType() == NotificationType.SYSTEM ? "Hệ thống" : "Nhóm mới";
        }
        return getStr(notification, "actorName");
    }

    public String resolveAvatar(Notification notification, String baseUrl) {
        // 1. Try group avatar first for group-related system events
        String convAvt = getStr(notification, "conversationAvatar");
        if (convAvt != null && !convAvt.isEmpty()) {
            return convAvt.startsWith("http") ? convAvt : baseUrl + convAvt;
        }

        // 2. Fallback to actor avatar
        String actorAvt = getStr(notification, "actorAvatar");
        if (actorAvt != null && !actorAvt.isEmpty()) {
            return actorAvt.startsWith("http") ? actorAvt : baseUrl + actorAvt;
        }

        return null;
    }

    private String renderGenericTitle(Notification notification, String locale, Map<String, Object> renderData) {
        try {
            NotificationTemplateResponse template = templateService.getTemplate(
                    notification.getType(),
                    NotificationChannel.FCM,
                    locale
            );
            return templateService.render(template.titleTemplate(), renderData);
        } catch (Exception e) {
            return "Notification";
        }
    }

    private Map<String, Object> prepareRenderData(Notification notification) {
        int actorCount = notification.getActorIds() != null ? notification.getActorIds().size() : 0;
        String lastActorName = getStr(notification, "actorName");
        String lastActorAvatar = getStr(notification, "actorAvatar");

        Map<String, Object> data = new HashMap<>(notification.getPayload() != null ? notification.getPayload() : Collections.emptyMap());
        data.put("actorCount", actorCount);
        data.put("othersCount", actorCount > 2 ? actorCount - 1 : 0);
        data.put("showSecondActor", actorCount == 2);
        data.put("actorName", lastActorName != null ? lastActorName : "");
        data.put("actorAvatar", lastActorAvatar != null ? lastActorAvatar : "");

        if (actorCount == 2) {
            String secondActorName = getStr(notification, "secondActorName");
            data.put("secondActorName", secondActorName != null ? secondActorName : "một người khác");
        }
        
        return data;
    }

    public String getStr(Notification n, String key) {
        if (n.getPayload() == null) return null;
        
        // 1. Try top-level payload
        Object v = n.getPayload().get(key);
        if (v != null) return v.toString();
        
        // 2. Try inside "metadata" map if exists
        Object metaObj = n.getPayload().get("metadata");
        if (metaObj instanceof Map<?, ?> metaMap) {
            // 2.1. Check directly in metadata
            Object metaVal = metaMap.get(key);
            if (metaVal != null) return metaVal.toString();
            
            // 2.2. Check inside "payload" map within metadata (for deep nesting)
            Object innerPayload = metaMap.get("payload");
            if (innerPayload instanceof Map<?, ?> innerMap) {
                Object innerVal = innerMap.get(key);
                if (innerVal != null) return innerVal.toString();
            }
        }
        
        return null;
    }
}
