package com.leafy.socialfeedservice.model;

import com.bondhub.common.model.BaseModel;
import com.leafy.socialfeedservice.model.embedded.PostMedia;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document("comments")
public class Comment extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    @Indexed
    String postId;

    @Indexed
    String authorId;

    @Indexed
    String parentId;

    String content;

    List<PostMedia> media;

    @Builder.Default
    int replyDepth = 0;

    @Builder.Default
    int replyCount = 0;

    @Builder.Default
    int reactionCount = 0;

    @Builder.Default
    boolean isEdited = false;
}
