package com.bondhub.notificationservices.service.delivery;

import com.bondhub.notificationservices.dto.response.template.NotificationTemplateResponse;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.service.template.NotificationTemplateService;
import com.bondhub.notificationservices.service.user.preference.UserPreferenceService;
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
public class NotificationStrategyHelper {

    NotificationTemplateService templateService;
    UserPreferenceService userPreferenceService;

    @Builder
    public record RenderedContent(String title, String body, String locale) {}

    public RenderedContent render(Notification notification, NotificationChannel channel, String forceLocale) {
        String recipientId = notification.getUserId();
        
        // 1. Determine Locale
        String locale = forceLocale;
        if (locale == null) {
            var userPrefs = userPreferenceService.getPreferences(recipientId);
            locale = (userPrefs != null) ? userPrefs.getLanguage() : "vi";
        }
        if (locale == null) locale = "vi";

        // 2. Prepare Render Data
        Map<String, Object> renderData = prepareRenderData(notification);

        // 3. Get Template
        NotificationTemplateResponse template = templateService.getTemplate(
                notification.getType(),
                channel,
                locale
        );

        // 4. Render
        String title = templateService.render(template.titleTemplate(), renderData);
        String body = templateService.render(template.bodyTemplate(), renderData);

        return RenderedContent.builder()
                .title(title)
                .body(body)
                .locale(locale)
                .build();
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

    private String getStr(Notification n, String key) {
        if (n.getPayload() == null) return null;
        Object v = n.getPayload().get(key);
        return v != null ? v.toString() : null;
    }
}
