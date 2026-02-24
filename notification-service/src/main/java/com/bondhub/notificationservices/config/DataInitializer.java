package com.bondhub.notificationservices.config;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.model.NotificationTemplate;
import com.bondhub.notificationservices.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    final NotificationTemplateRepository templateRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void seedTemplates() {
        seedIfAbsent(NotificationType.FRIEND_REQUEST, NotificationChannel.IN_APP, "vi",
                "{{firstName}} da gui loi moi ket ban",
                "Ban co {{count}} loi moi ket ban moi");
        seedIfAbsent(NotificationType.FRIEND_REQUEST, NotificationChannel.FCM, "vi",
                "{{firstName}} da gui loi moi ket ban",
                "Ban co {{count}} loi moi ket ban moi");
        seedIfAbsent(NotificationType.FRIEND_REQUEST, NotificationChannel.IN_APP, "en",
                "{{firstName}} sent you a friend request",
                "You have {{count}} new friend request(s)");
        seedIfAbsent(NotificationType.FRIEND_REQUEST, NotificationChannel.FCM, "en",
                "{{firstName}} sent you a friend request",
                "You have {{count}} new friend request(s)");
    }

    private void seedIfAbsent(NotificationType type, NotificationChannel channel, String locale,
                               String titleTemplate, String bodyTemplate) {
        if (templateRepository.findByTypeAndChannelAndLocaleAndActiveTrue(type, channel, locale).isPresent()) return;
        templateRepository.save(NotificationTemplate.builder()
                .type(type)
                .channel(channel)
                .locale(locale)
                .titleTemplate(titleTemplate)
                .bodyTemplate(bodyTemplate)
                .build());
        log.info("Seeded template: type={}, channel={}, locale={}", type, channel, locale);
    }
}