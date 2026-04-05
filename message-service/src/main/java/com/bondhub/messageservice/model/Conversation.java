package com.bondhub.messageservice.model;

import com.bondhub.common.model.BaseModel;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
@CompoundIndexes({
    @CompoundIndex(
        name = "members_userId_lastMessage_timestamp_idx",
        def = "{'members.userId': 1, 'lastMessage.timestamp': -1}"
    )
})
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class Conversation extends BaseModel {
    @Id
    String id;

    String name;
    String avatar;

    @Indexed
    @Builder.Default
    boolean isGroup = false;

    @Indexed
    @Builder.Default
    Set<ConversationMember> members = new HashSet<>();

    LastMessageInfo lastMessage;

    @Builder.Default
    Map<String, Integer> unreadCounts = new HashMap<>();

    @Indexed
    @Builder.Default
    boolean isDisbanded = false;
}
