package com.bondhub.authservice.model;

import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.common.model.BaseModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;

@Document("devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Device extends BaseModel {
    @MongoId
    String id;

    @Indexed
    String deviceId;

    @Indexed
    String sessionId;

    @Indexed
    String accountId;

    String deviceName;
    String browser;
    String os;
    DeviceType deviceType;
    String ipAddress;
    LocalDateTime lastActiveTime;
}
