package com.bondhub.messageservice.dto.request;

public record MarkAsReadRequest(
        String lastReadMessageId
) {}
