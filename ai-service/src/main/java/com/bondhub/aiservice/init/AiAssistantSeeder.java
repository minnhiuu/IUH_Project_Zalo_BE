package com.bondhub.aiservice.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiAssistantSeeder implements CommandLineRunner {
    private final ReactiveMongoTemplate mongoTemplate;
    private static final String COLLECTION_NAME = "chat_users";

    @Override
    public void run(String... args) {
        String assistantId = "ai-assistant-001";
        mongoTemplate.exists(Query.query(Criteria.where("_id").is(assistantId)), COLLECTION_NAME)
                .flatMap(exists -> {
                    if (!exists) {
                        log.info("Seeding AI Assistant user...");
                        Map<String, Object> doc = new HashMap<>();
                        doc.put("_id", assistantId);
                        doc.put("fullName", "BondHub Assistant");
                        doc.put("isBot", true);
                        return mongoTemplate.save(doc, COLLECTION_NAME);
                    }
                    return mongoTemplate.findById(assistantId, Map.class, COLLECTION_NAME);
                })
                .subscribe(u -> log.info("AI Assistant ready."));
    }
}
