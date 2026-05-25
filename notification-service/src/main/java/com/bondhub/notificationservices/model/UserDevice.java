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
import java.time.DayOfWeek;
import java.util.List;

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

    // Notification Preferences Snapshot
    @Builder.Default
    boolean allowNotifications = true;
    @Builder.Default
    boolean notifSound = true;
    @Builder.Default
    boolean notifVibration = true;
    @Builder.Default
    boolean notifMessages = true;
    @Builder.Default
    boolean notifGroups = true;
    @Builder.Default
    boolean notifFriendRequests = true;

    // Do Not Disturb Snapshot
    @Builder.Default
    boolean dndEnabled = false;
    String dndStartTime;
    String dndEndTime;
    @Builder.Default
    String dndTimezone = "GMT+07:00";
    List<DayOfWeek> activeDays;
}
