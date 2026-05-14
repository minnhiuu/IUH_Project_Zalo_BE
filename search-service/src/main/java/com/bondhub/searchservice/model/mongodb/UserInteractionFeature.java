package com.bondhub.searchservice.model.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "user_interaction_features")
@CompoundIndexes({
        @CompoundIndex(name = "user_target_unique_idx", def = "{'userId': 1, 'targetUserId': 1}", unique = true),
        @CompoundIndex(name = "user_updated_idx", def = "{'userId': 1, 'updatedAt': -1}")
})
public class UserInteractionFeature {

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String userId;

    @Field(targetType = FieldType.OBJECT_ID)
    private String targetUserId;

    private double chatScore;

    private double socialFeedScore;

    private double recentInteractionScore;

    private int messageCount30d;

    private Instant lastMessageAt;

    private int viewCount30d;

    private int reactionCount30d;

    private int commentCount30d;

    private int dislikeCount30d;

    private Instant lastSocialInteractionAt;

    private Instant updatedAt;

    public static String idFor(String userId, String targetUserId) {
        return userId + ":" + targetUserId;
    }
}
