package com.bondhub.messageservice.model;

import com.bondhub.common.enums.MessageType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PinnedMessageInfo {
    String messageId;
    String pinnedBy;
    String pinnedByName;
    String contentSnapshot;
    MessageType messageType;
    LocalDateTime pinnedAt;
}
