package com.bondhub.messageservice.model;

import com.bondhub.common.model.BaseModel;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class Conversation extends BaseModel {
    @Id
    String id;
    @Indexed(unique = true)
    String chatId;
    String senderId;
    String recipientId;
    String lastMessage;
    String lastMessageId;

    // later will replace with modifiedAt (from BaseModel)
    @Indexed(direction = IndexDirection.DESCENDING)
    LocalDateTime lastMessageTime;

    @Builder.Default
    Map<String, Integer> unreadCounts = new HashMap<>();

    @Builder.Default
    Set<ConversationMember> members = new HashSet<>();

    @Builder.Default
    boolean isGroup = false;
}
