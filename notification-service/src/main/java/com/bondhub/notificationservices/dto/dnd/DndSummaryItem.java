package com.bondhub.notificationservices.dto.dnd;

import lombok.Builder;

@Builder
public record DndSummaryItem(
        String category,
        String groupKey,
        int count,
        String title,
        String body
) {
}
