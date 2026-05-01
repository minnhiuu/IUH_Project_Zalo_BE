package com.bondhub.notificationservices.listener;

import com.bondhub.common.event.notification.EmailNotificationEvent;
import com.bondhub.notificationservices.service.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationListener {

    private final MailService mailService;

    @KafkaListener(topics = "email-notifications", groupId = "${spring.kafka.consumer.group-id}")
    public void handleEmailNotification(EmailNotificationEvent event) {
        log.info("Received email notification event for: {}", event.getRecipientEmail());
        try {
            mailService.sendEmail(
                event.getRecipientEmail(),
                event.getSubject(),
                event.getTemplateId(),
                event.getTemplateParams()
            );
        } catch (Exception e) {
            log.error("Failed to process email notification: {}", e.getMessage(), e);
        }
    }
}
