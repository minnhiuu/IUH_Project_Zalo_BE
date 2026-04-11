package com.bondhub.messageservice.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.messageservice.model.enums.JoinRequestStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "join_requests")
@CompoundIndexes({
    @CompoundIndex(name = "conv_status_idx", def = "{'conversationId': 1, 'status': 1}"),
    @CompoundIndex(name = "conv_user_status_idx", def = "{'conversationId': 1, 'userId': 1, 'status': 1}")
})
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JoinRequest extends BaseModel {

    @Id
    String id;

    @Indexed
    String conversationId;

    @Indexed
    String userId;

    @Builder.Default
    JoinRequestStatus status = JoinRequestStatus.PENDING;

    LocalDateTime processedAt;
    String processedBy;
}
