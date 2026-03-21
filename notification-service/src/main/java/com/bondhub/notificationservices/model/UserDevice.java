package com.bondhub.notificationservices.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.notificationservices.enums.Platform;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document("user_devices")
@CompoundIndexes({
        @CompoundIndex(name = "user_device_idx", def = "{'userId': 1, 'fcmToken': 1}", unique = true)
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDevice extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    @Field(targetType = FieldType.OBJECT_ID)
    String userId;

    String fcmToken;

    String deviceId;

    String locale;

    Platform platform;
}
