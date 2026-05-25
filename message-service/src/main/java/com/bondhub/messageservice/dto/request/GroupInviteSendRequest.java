package com.bondhub.messageservice.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record GroupInviteSendRequest(
        @NotEmpty(message = "validation.chat.group.invites.required")
        List<String> userIds
) {
}
