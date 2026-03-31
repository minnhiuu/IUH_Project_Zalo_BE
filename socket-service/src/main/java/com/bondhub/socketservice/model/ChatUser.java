package com.bondhub.socketservice.model;

import com.bondhub.common.enums.Status;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_users")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatUser {
    @Id
    String id;
    String accountId;
    String fullName;
    String email;
    Status status;
    String avatar;
    LocalDateTime lastUpdatedAt;

    @Indexed
    @Builder.Default
    Set<String> friendIds = new HashSet<>();

    @Builder.Default
    boolean isInvisible = false;

    @Builder.Default
    boolean showSeenStatus = true;
}
