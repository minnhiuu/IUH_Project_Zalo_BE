package com.bondhub.friendservice.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.friendservice.model.enums.FriendStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document("friendships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FriendShip extends BaseModel {
    @EqualsAndHashCode.Include
    @MongoId(FieldType.OBJECT_ID)
    String id;

    String content;
    FriendStatus friendStatus;

    @Field(targetType = FieldType.OBJECT_ID)
    String requested;
    @Field(targetType = FieldType.OBJECT_ID)
    String received;

}
