package com.bondhub.notificationservices.dto.request.template;

public record UpdateTemplateRequest(

        String titleTemplate,

        String bodyTemplate,

        Boolean active
) {}
