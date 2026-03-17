package com.bondhub.messageservice.model;

import com.bondhub.common.enums.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.bondhub.messageservice.model.enums.MessageType;
import java.time.LocalDateTime;

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
}
