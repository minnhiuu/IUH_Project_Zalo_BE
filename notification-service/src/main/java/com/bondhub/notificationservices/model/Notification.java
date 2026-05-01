package com.bondhub.notificationservices.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.common.enums.NotificationType;
import lombok.*;
import lombok.experimental.SuperBuilder;
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
        @CompoundIndex(name = "user_last_modified_idx",
                def = "{'userId': 1, 'lastModifiedAt': -1}"),
        @CompoundIndex(name = "user_unread_idx",
                def = "{'userId': 1, 'isRead': 1}"),
        @CompoundIndex(name = "user_type_reference_unique",
                def = "{'userId': 1, 'type': 1, 'referenceId': 1}",
                unique = true)
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class Notification extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    @Field(targetType = FieldType.OBJECT_ID)
    String userId;

    NotificationType type;

    @Field(targetType = FieldType.OBJECT_ID)
    String referenceId;

    @Field(targetType = FieldType.OBJECT_ID)
    List<String> actorIds;

    Map<String, Object> payload;

    boolean isRead;

    LocalDateTime readAt;
}
