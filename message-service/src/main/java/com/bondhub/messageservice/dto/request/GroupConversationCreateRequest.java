package com.bondhub.messageservice.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record GroupConversationCreateRequest(
        String name,

        String avatar,

        @NotEmpty(message = "validation.chat.group.members.required")
        List<String> memberIds
) {
}
