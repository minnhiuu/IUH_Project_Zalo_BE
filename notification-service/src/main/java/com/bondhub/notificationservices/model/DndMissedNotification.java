package com.bondhub.notificationservices.model;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.model.BaseModel;
import com.bondhub.notificationservices.enums.DndMissedStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;
import java.util.Map;

@Document("dnd_missed_notifications")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@CompoundIndexes({
        @CompoundIndex(
                name = "user_status_occurred_idx",
                def = "{'userId': 1, 'status': 1, 'occurredAt': -1}"
        ),
        @CompoundIndex(
                name = "user_summary_group_idx",
                def = "{'userId': 1, 'summaryGroupKey': 1, 'status': 1}"
        )
})
public class DndMissedNotification extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    @Field(targetType = FieldType.OBJECT_ID)
    String userId;

    NotificationType type;

    @Field(targetType = FieldType.OBJECT_ID)
    String referenceId;

    @Field(targetType = FieldType.OBJECT_ID)
    String conversationId;

    @Field(targetType = FieldType.OBJECT_ID)
    String postId;

    @Field(targetType = FieldType.OBJECT_ID)
    String actorId;

    String actorName;

    String groupName;

    String summaryGroupKey;

    Map<String, Object> payload;

    @Builder.Default
    DndMissedStatus status = DndMissedStatus.PENDING;

    LocalDateTime occurredAt;

    LocalDateTime summarizedAt;
}
