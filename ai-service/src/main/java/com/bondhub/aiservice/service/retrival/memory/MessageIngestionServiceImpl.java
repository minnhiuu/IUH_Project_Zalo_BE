package com.bondhub.aiservice.service.retrival.memory;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageIngestionServiceImpl implements MessageIngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Override
    public void ingest(String messageId, String content, String userId, String chatId) {
        if (content == null || content.isBlank()) return;

        log.info("[Ingestion] Vectorizing message: {} [userId: {}, chatId: {}]", messageId, userId, chatId);

        Metadata metadata = new Metadata();
        metadata.put("message_id", messageId);
        metadata.put("user_id", userId);
        metadata.put("chat_id", chatId);

        TextSegment segment = TextSegment.from(content, metadata);

        Embedding embedding = embeddingModel.embed(content).content();
        embeddingStore.add(embedding, segment);

        log.info("[Ingestion] Successfully indexed message to Qdrant vector store.");
    }
}
