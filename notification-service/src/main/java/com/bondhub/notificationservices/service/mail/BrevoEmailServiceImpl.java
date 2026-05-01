package com.bondhub.notificationservices.service.mail;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sendinblue.ApiClient;
import sendinblue.ApiException;
import sendinblue.Configuration;
import sendinblue.auth.ApiKeyAuth;
import sibApi.TransactionalEmailsApi;
import sibModel.*;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class BrevoEmailServiceImpl implements MailService {

    @Value("${brevo.api-key}")
    String brevoApiKey;

    @Value("${BREVO_MAIL}")
    String fromEmail;

    @Value("${brevo.sender.name:BondHub}")
    String senderName;

    @Override
    public void sendEmail(String to, String subject, String templateId, Map<String, Object> params) {
        try {
            Long parsedTemplateId = Long.valueOf(templateId);

            log.info("Sending email via Brevo template to: {}", to);

            // Configure Brevo API client
            ApiClient defaultClient = Configuration.getDefaultApiClient();
            ApiKeyAuth apiKey = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
            apiKey.setApiKey(brevoApiKey);

            // Create transactional emails API instance
            TransactionalEmailsApi apiInstance = new TransactionalEmailsApi();

            // Create sender
            SendSmtpEmailSender sender = new SendSmtpEmailSender();
            sender.setName(senderName);
            sender.setEmail(fromEmail);

            // Create recipient
            SendSmtpEmailTo recipient = new SendSmtpEmailTo();
            recipient.setEmail(to);

            // Create email object
            SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();
            sendSmtpEmail.setSender(sender);
            sendSmtpEmail.setTo(List.of(recipient));
            sendSmtpEmail.setTemplateId(parsedTemplateId);
            sendSmtpEmail.setParams(params);
            sendSmtpEmail.setSubject(subject);

            // Send email
            CreateSmtpEmail result = apiInstance.sendTransacEmail(sendSmtpEmail);

            log.info("✅ Email sent successfully via Brevo. Message ID: {}", result.getMessageId());

        } catch (ApiException e) {
            log.error("❌ Brevo API error while sending email to: {}", to);
            log.error("Status code: {}; Reason: {}", e.getCode(), e.getResponseBody());
            throw new RuntimeException("Failed to send email via Brevo: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error while sending email to: {}", to);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
