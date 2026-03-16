package com.bondhub.notificationservices.service.template;

import com.bondhub.notificationservices.dto.request.template.CreateTemplateRequest;
import com.bondhub.notificationservices.dto.request.template.UpdateTemplateRequest;
import com.bondhub.notificationservices.dto.response.template.NotificationTemplateResponse;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.common.enums.NotificationType;

import java.util.List;
import java.util.Map;

public interface NotificationTemplateService {

    NotificationTemplateResponse create(CreateTemplateRequest request);

    NotificationTemplateResponse update(String id, UpdateTemplateRequest request);

    NotificationTemplateResponse getTemplate(NotificationType type, NotificationChannel channel, String locale);

    Map<NotificationType, NotificationTemplateResponse> getTemplates(List<NotificationType> types, NotificationChannel channel, String locale);

    String render(String template, Map<String, Object> payload);
}
