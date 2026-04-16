package com.bondhub.messageservice.model;

import com.bondhub.common.enums.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.bondhub.common.enums.MessageType;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LastMessageInfo {
    String messageId;
    String senderId;
    String content;
    LocalDateTime timestamp;
    MessageType type;
    MessageStatus status;
    Map<String, Object> metadata;
    Set<String> visibleTo;
}
