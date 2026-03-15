package com.bondhub.authservice.dto.auth.request;

import jakarta.validation.constraints.NotBlank;

public record QrMobileRequest(
        @NotBlank(message = "{validation.qrContent.required}")
        String qrContent
) {}
