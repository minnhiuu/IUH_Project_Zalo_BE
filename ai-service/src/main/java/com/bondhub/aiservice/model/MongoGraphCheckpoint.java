package com.bondhub.aiservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ai_graph_checkpoints")
public class MongoGraphCheckpoint {

    @Id
    private String id; // convId

    private String conversationId;
    private String userId;
    private String originalQuery;
    private String missingFieldInfo;
    private int retryCount;

    private long updatedAt;

    @Indexed(name = "ttl_ai_graph_checkpoints", expireAfterSeconds = 0)
    private Instant expireAt;
}
