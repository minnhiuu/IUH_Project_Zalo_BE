package com.bondhub.notificationservices.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.common.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document("notification_templates")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@CompoundIndexes({
        @CompoundIndex(
                name = "type_channel_locale_unique",
                def = "{'type': 1, 'channel': 1, 'locale': 1}",
                unique = true
        )
})
public class NotificationTemplate extends BaseModel {
    @MongoId(FieldType.OBJECT_ID)
    String id;

    NotificationType type;

    NotificationChannel channel;

    String locale;

    String titleTemplate;

    String bodyTemplate;
}
