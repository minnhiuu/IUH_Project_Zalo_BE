package com.bondhub.notificationservices.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.notificationservices.enums.NotificationType;
import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Document("notifications")
@CompoundIndexes({
        @CompoundIndex(name = "user_created_idx",
                def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "user_unread_idx",
                def = "{'userId': 1, 'isRead': 1}"),
        @CompoundIndex(name = "user_type_reference_unique",
                def = "{'userId': 1, 'type': 1, 'referenceId': 1}",
                unique = true)
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Notification extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    @Field(targetType = FieldType.OBJECT_ID)
    String userId;

    NotificationType type;

    @Field(targetType = FieldType.OBJECT_ID)
    String referenceId;

    String title;

    String body;

    @Field(targetType = FieldType.OBJECT_ID)
    List<String> actorIds;

    Map<String, Object> data;

    boolean isRead;

    LocalDateTime readAt;
}
