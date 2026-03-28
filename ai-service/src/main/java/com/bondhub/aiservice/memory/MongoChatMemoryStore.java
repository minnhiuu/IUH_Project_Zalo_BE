package com.bondhub.aiservice.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MongoChatMemoryStore implements ChatMemoryStore {
    private final MongoTemplate mongoTemplate;
    private static final String COLLECTION_NAME = "ai_chat_memory";

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        log.debug("Retrieving chat memory for: {}", memoryId);
        ChatMemoryEntity entity = mongoTemplate.findOne(
                Query.query(Criteria.where("chatId").is(memoryId.toString())), 
                ChatMemoryEntity.class, 
                COLLECTION_NAME
        );
        
        if (entity == null || entity.getMessages() == null) return new ArrayList<>();
        
        // Filter out nulls from old/malformed documents that lack 'type' field
        return entity.getMessages().stream()
                .filter(m -> m != null)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        log.debug("Updating chat memory for: {}", memoryId);
        ChatMemoryEntity entity = ChatMemoryEntity.builder()
                .chatId(memoryId.toString())
                .messages(messages)
                .build();
        mongoTemplate.save(entity, COLLECTION_NAME);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        log.debug("Deleting chat memory for: {}", memoryId);
        mongoTemplate.remove(
                Query.query(Criteria.where("chatId").is(memoryId.toString())), 
                COLLECTION_NAME
        );
    }
}
