package com.bondhub.messageservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record GroupConversationCreateRequest(
        @NotBlank(message = "validation.chat.group.name.required")
        String name,

        String avatar,

        @NotEmpty(message = "validation.chat.group.members.required")
        List<String> memberIds
) {
}
