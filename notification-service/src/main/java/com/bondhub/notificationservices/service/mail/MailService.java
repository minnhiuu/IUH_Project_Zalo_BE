package com.bondhub.notificationservices.service.mail;

import java.util.Map;

public interface MailService {
    void sendEmail(String to, String subject, String htmlContent);
}
