package com.leafy.socialfeedservice.model;

import com.bondhub.common.model.BaseModel;
import com.leafy.socialfeedservice.model.embedded.PostContent;
import com.leafy.socialfeedservice.model.embedded.PostMedia;
import com.leafy.socialfeedservice.model.embedded.PostStats;
import com.leafy.socialfeedservice.model.enums.PostType;
import com.leafy.socialfeedservice.model.enums.Visibility;
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

import java.time.LocalDateTime;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document("posts")
public class Post extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    @Indexed
    String authorId;

    @Indexed
    String groupId;

    PostContent content;

    List<PostMedia> media;

    PostType postType;

    Visibility visibility;

    PostStats stats;

    LocalDateTime uploadedAt;

    LocalDateTime updatedAt;

    @Builder.Default
    int version = 1;

    @Builder.Default
    boolean isCurrent = true;

    @Builder.Default
    boolean isEdited = false;
}
