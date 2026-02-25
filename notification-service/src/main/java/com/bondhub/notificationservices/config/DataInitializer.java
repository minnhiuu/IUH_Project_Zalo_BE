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
                "Lời mời kết bạn",
                "{{firstName}}{{#othersCount}} và {{othersCount}} người khác{{/othersCount}} đã gửi lời mời kết bạn cho bạn");
        seedIfAbsent(NotificationType.FRIEND_REQUEST, NotificationChannel.FCM, "vi",
                "Lời mời kết bạn",
                "{{firstName}}{{#othersCount}} và {{othersCount}} người khác{{/othersCount}} đã gửi lời mời kết bạn cho bạn");
        seedIfAbsent(NotificationType.FRIEND_REQUEST, NotificationChannel.IN_APP, "en",
                "Friend Request",
                "{{firstName}}{{#othersCount}} and {{othersCount}} others{{/othersCount}} sent you a friend request");
        seedIfAbsent(NotificationType.FRIEND_REQUEST, NotificationChannel.FCM, "en",
                "Friend Request",
                "{{firstName}}{{#othersCount}} and {{othersCount}} others{{/othersCount}} sent you a friend request");
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