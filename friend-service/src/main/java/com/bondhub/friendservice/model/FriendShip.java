package com.bondhub.friendservice.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.friendservice.model.enums.FriendStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document("friendships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FriendShip extends BaseModel {
    @EqualsAndHashCode.Include
    @MongoId
    String id;

    String content;
    FriendStatus friendStatus;
    String requested;
    String received;

}
