package com.bondhub.authservice.service.mail;

import com.bondhub.authservice.config.MailTemplate;
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

import java.util.HashMap;
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
    public void sendOtpEmail(String email, String otp, String templateId, String accountId) {
        try {
            Long parsedTemplateId = Long.valueOf(templateId);

            log.info("=== BREVO EMAIL DEBUG INFO ===");
            log.info("Sending OTP email via Brevo template to: {}", email);
            log.info("Template ID: {}", parsedTemplateId);
            log.info("From: {} <{}>", senderName, fromEmail);
            log.info("OTP: {}", otp);
            log.info("Account ID: {}", accountId);

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
            recipient.setEmail(email);

            // Create template parameters
            Map<String, Object> templateParams = new HashMap<>();
            templateParams.put("otpCode", otp);
            templateParams.put("accountId", accountId);
            templateParams.put("companyName", "BondHub");
            templateParams.put("companyTagline", "Connecting people through shared bonds");
            templateParams.put("supportEmail", "support@bondhub.com");
            templateParams.put("currentYear", String.valueOf(java.time.Year.now().getValue()));

            // Create email object
            SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();
            sendSmtpEmail.setSender(sender);
            sendSmtpEmail.setTo(List.of(recipient));
            sendSmtpEmail.setTemplateId(parsedTemplateId);
            sendSmtpEmail.setParams(templateParams);

            // Send email
            CreateSmtpEmail result = apiInstance.sendTransacEmail(sendSmtpEmail);

            log.info("✅ OTP email sent successfully via Brevo template");
            log.info("Message ID: {}", result.getMessageId());

        } catch (ApiException e) {
            log.error("❌ Brevo API error while sending OTP email to: {}", email);
            log.error("Status code: {}", e.getCode());
            log.error("Reason: {}", e.getResponseBody());
            log.error("Response headers: {}", e.getResponseHeaders());

            throw new RuntimeException("Failed to send OTP email via Brevo template: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error while sending OTP email to: {}", email);
            log.error("Error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }

    @Override
    public void sendPasswordResetOtpEmail(String email, String otp) {
        sendOtpEmail(email, otp, MailTemplate.FORGOT_PASSWORD_OTP_TEMPLATE_ID, "Password Reset Request");
    }
}
