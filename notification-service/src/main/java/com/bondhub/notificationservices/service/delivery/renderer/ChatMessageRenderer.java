package com.bondhub.notificationservices.service.delivery.renderer;

import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.service.template.NotificationTemplateService;
import com.bondhub.notificationservices.dto.response.template.NotificationTemplateResponse;
import com.bondhub.notificationservices.service.delivery.renderer.internal.MediaLabelBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageRenderer implements NotificationRenderer {

    private static final int FCM_CHAT_VISIBLE_LINES = 4;
    private final NotificationTemplateService templateService;

    @Override
    public String renderBody(Notification notification, NotificationChannel channel, String locale, Map<String, Object> renderData) {
        Object snippetsObj = notification.getPayload().get("snippets");
        if (snippetsObj instanceof java.util.List<?> snippets && !snippets.isEmpty()) {
            var lines = snippets.stream()
                    .map(Object::toString)
                    .map(line -> rebuildMediaLabel(line, notification, locale))
                    .map(line -> line.replaceAll("\\s+", " ").trim())
                    .filter(line -> !line.isEmpty())
                    .toList();

            if (channel == NotificationChannel.FCM && lines.size() > FCM_CHAT_VISIBLE_LINES) {
                lines = lines.subList(lines.size() - FCM_CHAT_VISIBLE_LINES, lines.size());
            }

            return String.join("\n", lines);
        }

        try {
            NotificationTemplateResponse template = templateService.getTemplate(
                    notification.getType(),
                    channel,
                    locale
            );
            
            Map<String, Object> templateData = new HashMap<>(renderData);
            if (templateData.containsKey("imageCount") || templateData.containsKey("videoCount")) {
                int imageCount = ((Number) templateData.getOrDefault("imageCount", 0)).intValue();
                int videoCount = ((Number) templateData.getOrDefault("videoCount", 0)).intValue();
                String imageLabel = MediaLabelBuilder.buildImageLabel(imageCount, locale);
                String videoLabel = MediaLabelBuilder.buildVideoLabel(videoCount, locale);
                templateData.put("imageLabel", imageLabel);
                templateData.put("videoLabel", videoLabel);
            }
            
            return templateService.render(template.bodyTemplate(), templateData);
        } catch (Exception e) {
            log.warn("Failed to render chat message via template, using fallback", e);
            String content = getLocalizedContent(notification, locale);
            if (Boolean.TRUE.equals(notification.getPayload().get("isGroup"))) {
                String actorName = getString(notification, "actorName");
                return actorName + ": " + content;
            }
            return content;
        }
    }

    private String rebuildMediaLabel(String line, Notification notification, String locale) {
        if (line == null || !line.matches(".*\\[.*?(?:Photo|Ảnh|Video|video|Other).*?\\].*")) {
            return line;
        }

        try {
            // Detect counts and plurality from the line to support history snippets
            boolean isPlural = line.matches(".*\\[.*?(?:Photos|Nhiều ảnh|Videos|Nhiều video).*?\\].*");
            int imageCount = line.matches(".*\\[.*?(?:Photo|Ảnh).*?\\].*") ? (isPlural ? 2 : 1) : 0;
            int videoCount = line.matches(".*\\[.*?(?:Video|video).*?\\].*") ? (isPlural ? 2 : 1) : 0;

            String rebuiltLabel = MediaLabelBuilder.buildMediaLabel(imageCount, videoCount, locale);
            return line.replaceAll("\\[.*?(?:Photo|Ảnh|Video|video|Other).*?\\]", rebuiltLabel);
        } catch (Exception e) {
            return line;
        }
    }

    private String getLocalizedContent(Notification notification, String locale) {
        String localized = getString(notification, "en".equalsIgnoreCase(locale) ? "contentEn" : "contentVi");
        if (localized != null && !localized.isBlank()) {
            return rebuildMediaLabel(localized, notification, locale);
        }
        String fallback = getString(notification, "content");
        if (fallback != null && !fallback.isBlank()) {
            return rebuildMediaLabel(fallback, notification, locale);
        }
        return fallback;
    }

    private String getString(Notification n, String key) {
        if (n.getPayload() == null) return null;
        
        Object v = n.getPayload().get(key);
        if (v != null) return v.toString();
        
        Object metaObj = n.getPayload().get("metadata");
        if (metaObj instanceof Map<?, ?> metaMap) {
            Object metaVal = metaMap.get(key);
            if (metaVal != null) return metaVal.toString();
            
            Object innerPayload = metaMap.get("payload");
            if (innerPayload instanceof Map<?, ?> innerMap) {
                Object innerVal = innerMap.get(key);
                if (innerVal != null) return innerVal.toString();
            }
        }
        
        return null;
    }
}
