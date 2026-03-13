package com.bondhub.userservice.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.userservice.model.enums.UserAction;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * Entity representing user activity log entries
 */
@Document("user_activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
@CompoundIndexes({
    @CompoundIndex(name = "user_action_idx", def = "{'userId': 1, 'action': 1}"),
    @CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}")
})
public class UserActivityLog extends BaseModel {
    
    @EqualsAndHashCode.Include
    @MongoId
    String id;
    
    String userId;
    UserAction action;
    String description;
    String ipAddress;
    String userAgent;
    ActivityLogMetadata metadata;
}
