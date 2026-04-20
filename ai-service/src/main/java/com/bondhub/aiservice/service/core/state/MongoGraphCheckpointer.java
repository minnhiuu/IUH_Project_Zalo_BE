package com.bondhub.aiservice.service.core.state;

import com.bondhub.aiservice.model.CragState;
import com.bondhub.aiservice.model.MongoGraphCheckpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class MongoGraphCheckpointer {

    private final MongoTemplate mongoTemplate;

    public void saveWaitingContext(CragState state) {
        if (state == null || state.getConvId() == null) {
            return;
        }
        Instant now = Instant.now();
        MongoGraphCheckpoint checkpoint = MongoGraphCheckpoint.builder()
                .id(state.getConvId())
                .conversationId(state.getConversationId())
                .userId(state.getUserId())
                .originalQuery(state.getOriginalQuery())
                .missingFieldInfo(state.getMissingFieldInfo())
                .retryCount(state.getRetryCount())
                .updatedAt(now.toEpochMilli())
                .expireAt(now.plusSeconds(172800))
                .build();
        mongoTemplate.save(checkpoint);
        log.debug("[Graph] Saved checkpoint for convId={}", state.getConvId());
    }

    public MongoGraphCheckpoint load(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return null;
        }
        MongoGraphCheckpoint checkpoint = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(threadId)),
                MongoGraphCheckpoint.class
        );
        if (checkpoint != null) {
            log.debug("[Graph] Loaded checkpoint for convId={}", threadId);
        }
        return checkpoint;
    }

    public void clear(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return;
        }
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(threadId)), MongoGraphCheckpoint.class);
        log.debug("[Graph] Cleared checkpoint for convId={}", threadId);
    }
}
