package com.bondhub.notificationservices.service.delivery.strategy;

import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.service.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailDeliveryStrategy implements NotificationStrategy {

    private final MailService mailService;

    @Override
    public void execute(Notification notification) {
        // Logic to determine if this specific notification should also be sent via email
        // For example, if it's a high-priority system alert or if the user specifically opted in.
        
        // This is a placeholder for actual business rules
        log.info("EmailDeliveryStrategy: Checking if email should be sent for notification {}", notification.getId());
        
        // Example: mailService.sendEmail(recipientEmail, subject, processedHtml);
    }
}
