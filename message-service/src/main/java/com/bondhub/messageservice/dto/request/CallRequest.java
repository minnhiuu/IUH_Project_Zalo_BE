package com.bondhub.messageservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CallRequest(
        @NotBlank(message = "validation.call.receiver.required")
        String receiverId
) {
}
