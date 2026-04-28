package com.bondhub.searchservice.dto.request;

import jakarta.validation.constraints.Pattern;
import java.util.Locale;

public record MessageSearchRequest(
        String keyword,

        String conversationId,

        String senderId,

        Long from,

        Long to,

        @Pattern(regexp = "^(7d|30d|3months)?$", message = "validation.message.search.dateRange.invalid")
        String dateRange
) {
    public MessageSearchRequest {
        keyword = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        conversationId = conversationId != null && !conversationId.isBlank() ? conversationId.trim() : null;
        senderId = senderId != null && !senderId.isBlank() ? senderId.trim() : null;
        dateRange = dateRange != null && !dateRange.isBlank()
                ? dateRange.trim().toLowerCase(Locale.ROOT)
                : null;
    }
}
