package com.bondhub.notificationservices.service.mail;

import java.util.Map;

public interface MailService {
    void sendEmail(String to, String subject, String templateId, Map<String, Object> params);
    void sendHtmlEmail(String to, String subject, String htmlContent);
}
