package com.bondhub.common.dto;

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
