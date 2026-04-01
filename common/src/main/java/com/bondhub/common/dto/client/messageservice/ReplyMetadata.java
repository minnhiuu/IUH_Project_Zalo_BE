package com.bondhub.common.dto.client.messageservice;

import com.bondhub.common.enums.MessageType;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyMetadata {
    String messageId;
    String senderId;
    String content;
    MessageType type; 
}
