package com.bondhub.messageservice.model;

import com.bondhub.messageservice.model.enums.MessageType;
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
