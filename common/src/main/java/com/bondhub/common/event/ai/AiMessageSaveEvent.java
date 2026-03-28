package com.bondhub.common.event.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageSaveEvent {
    private String userId;
    private String chatId;
    private String content;
}
