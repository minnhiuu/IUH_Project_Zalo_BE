package com.bondhub.searchservice.model.mongodb;

import com.bondhub.searchservice.enums.SearchEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
@Document(collection = "search_events")
@CompoundIndexes({
        @CompoundIndex(name = "search_event_user_created_idx", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "search_event_user_keyword_idx", def = "{'userId': 1, 'normalizedKeyword': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "search_event_user_target_idx", def = "{'userId': 1, 'targetUserId': 1, 'createdAt': -1}")
})
public class SearchEvent {

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String userId;

    private String keyword;

    private String normalizedKeyword;

    @Field(targetType = FieldType.OBJECT_ID)
    private String targetUserId;

    private Integer rank;

    private SearchEventType eventType;

    private Instant createdAt;
}
