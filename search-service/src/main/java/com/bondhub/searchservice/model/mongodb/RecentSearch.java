package com.bondhub.searchservice.model.mongodb;

import com.bondhub.searchservice.enums.SearchType;
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

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "recent_searches")
@CompoundIndexes({
    @CompoundIndex(name = "user_target_idx", def = "{'userId': 1, 'targetId': 1, 'type': 1}"),
    @CompoundIndex(name = "user_keyword_idx", def = "{'userId': 1, 'name': 1, 'type': 1}")
})
public class RecentSearch {
    @MongoId(FieldType.OBJECT_ID)
    private String id;

    @Field(targetType =  FieldType.OBJECT_ID)
    private String userId;

    @Field(targetType =  FieldType.OBJECT_ID)
    private String targetId;
    private String name;
    private String avatar;
    private SearchType type;
    private long timestamp;
}
