package com.bondhub.messageservice.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddMembersRequest(
        @NotEmpty(message = "validation.chat.group.members.required")
        List<String> memberIds
) {
}
