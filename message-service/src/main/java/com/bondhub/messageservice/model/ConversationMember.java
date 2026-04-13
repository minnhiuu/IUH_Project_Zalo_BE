package com.bondhub.messageservice.model;

import com.bondhub.messageservice.model.enums.MemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMember {
    String userId;
    String lastReadMessageId;
    MemberRole role;
    LocalDateTime joinedAt;

    @Builder.Default
    Boolean active = true;

    LocalDateTime removedAt;
    String removedBy;
}
