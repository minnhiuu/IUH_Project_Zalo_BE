package com.bondhub.friendservice.model;

import com.bondhub.common.model.BaseModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document("block_list")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
@CompoundIndexes({
    @CompoundIndex(name = "blocker_blocked_idx", def = "{'blockerId': 1, 'blockedUserId': 1}", unique = true),
    @CompoundIndex(name = "blocker_idx", def = "{'blockerId': 1}")
})
public class BlockList extends BaseModel {

    @EqualsAndHashCode.Include
    @MongoId(FieldType.OBJECT_ID)
    String id;

    @Field(targetType =  FieldType.OBJECT_ID)
    String blockerId;

    @Field(targetType =  FieldType.OBJECT_ID)
    String blockedUserId;

    BlockPreference preference;
}
