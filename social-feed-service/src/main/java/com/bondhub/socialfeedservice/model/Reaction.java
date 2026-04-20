package com.bondhub.socialfeedservice.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.socialfeedservice.model.enums.ReactionTargetType;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document("reactions")
@CompoundIndex(name = "uniq_author_target", def = "{ 'authorId': 1, 'targetId': 1, 'targetType': 1 }", unique = true)
public class Reaction extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    ReactionType type;

    String authorId;

    String targetId;

    ReactionTargetType targetType;
}
