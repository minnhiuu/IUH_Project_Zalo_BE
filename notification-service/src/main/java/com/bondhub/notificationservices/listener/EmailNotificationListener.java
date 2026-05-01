package com.bondhub.notificationservices.listener;

import com.bondhub.common.constant.MailTemplate;
import com.bondhub.common.event.notification.EmailNotificationEvent;
import com.bondhub.notificationservices.service.mail.MailService;
import com.bondhub.notificationservices.service.template.NotificationTemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationListener {

    private final MailService mailService;
    private final NotificationTemplateEngine templateEngine;

    @KafkaListener(topics = "email-notifications", groupId = "${spring.kafka.consumer.group-id}")
    public void handleEmailNotification(EmailNotificationEvent event) {
        log.info("Received email notification event for: {}", event.getRecipientEmail());
        try {
            String templateName = getThymeleafTemplateName(event.getTemplateId());
            
            if (templateName == null) {
                log.error("No local template found for ID: {}. Skipping email delivery.", event.getTemplateId());
                return;
            }

            log.info("Processing Thymeleaf template: {}", templateName);
            String htmlContent = templateEngine.process(templateName, event.getTemplateParams());
            mailService.sendEmail(event.getRecipientEmail(), event.getSubject(), htmlContent);
            
            log.info("Email notification processed successfully for: {}", event.getRecipientEmail());
        } catch (Exception e) {
            log.error("Failed to process email notification for {}: {}", event.getRecipientEmail(), e.getMessage(), e);
        }
    }

    private String getThymeleafTemplateName(String templateId) {
        return switch (templateId) {
            case MailTemplate.REGISTRATION_OTP_TEMPLATE_ID -> "otp-verification";
            case MailTemplate.FORGOT_PASSWORD_OTP_TEMPLATE_ID -> "otp-verification";
            default -> null;
        };
    }
}
