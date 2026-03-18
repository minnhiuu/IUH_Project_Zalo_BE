package com.leafy.socialfeedservice.model;

import com.bondhub.common.event.socialfeed.InteractionType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document("user_interactions")
@CompoundIndexes({
        @CompoundIndex(name = "idx_user_id_created_at_desc", def = "{'user_id': 1, 'created_at': -1}"),
        @CompoundIndex(name = "idx_post_id_created_at_desc", def = "{'post_id': 1, 'created_at': -1}"),
        @CompoundIndex(name = "idx_group_id_created_at_desc", def = "{'group_id': 1, 'created_at': -1}")
})
public class UserInteraction {

    @Id
    String id;

    @Field("user_id")
    String userId;

    @Field("post_id")
    String postId;

    @Field("interaction_type")
    InteractionType interactionType;

    float weight;

    @Field("created_at")
    Instant createdAt;

    @Field("group_id")
    String groupId;

    SourceMetadata source;

    @Field("ingested_at")
    Instant ingestedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class SourceMetadata {
        String topic;
        int partition;
        long offset;

        @Field("message_key")
        String messageKey;
    }
}