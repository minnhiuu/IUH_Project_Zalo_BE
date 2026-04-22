package com.bondhub.messageservice.dto.request;

public record MessageCursorRequest(
    String cursor,
    Integer limit,
    String direction, // "OLDER" or "NEWER"
    String aroundMessageId
) {}
