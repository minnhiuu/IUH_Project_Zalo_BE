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
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_rooms")
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
    @Indexed(direction = IndexDirection.DESCENDING)
    LocalDateTime lastMessageTime;
    @Builder.Default
    Map<String, Integer> unreadCounts = new HashMap<>();
}
