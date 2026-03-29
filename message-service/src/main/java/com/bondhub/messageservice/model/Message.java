package com.bondhub.messageservice.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.common.enums.MessageStatus;
import com.bondhub.messageservice.model.enums.MessageType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;
import java.util.Set;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_messages")
@CompoundIndex(name = "conversationId_createdAt_idx", def = "{'conversationId': 1, 'createdAt': -1}")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class Message extends BaseModel {
    @Id
    String id;
    String conversationId;
    String senderId;
    String senderName;
    String senderAvatar;
    String recipientId;
    String content;
    String clientMessageId;
    MessageType type;
    ReplyMetadata replyTo;
    @Builder.Default
    boolean isForwarded = false;

    @Builder.Default
    MessageStatus status = MessageStatus.NORMAL;

    @Builder.Default
    Set<String> deletedBy = new HashSet<>();
}
