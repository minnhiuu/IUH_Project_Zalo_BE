package com.bondhub.aiservice.service;

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
public class MessageIngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * Nạp tin nhắn vào Qdrant Vector Store phục vụ RAG.
     */
    public void ingest(String messageId, String content, String userId, String chatId) {
        if (content == null || content.isBlank()) return;
        
        log.info("[Ingestion] Vectorizing message: {} [userId: {}, chatId: {}]", messageId, userId, chatId);
        
        // 1. Tạo Metadata (Crucial for filtering RAG results by conversation context)
        Metadata metadata = new Metadata();
        metadata.put("message_id", messageId);
        metadata.put("user_id", userId);
        metadata.put("chat_id", chatId); // ID căn phòng chat để lọc (conversationId)

        // 2. Tạo TextSegment (Nội dung kèm Metadata)
        TextSegment segment = TextSegment.from(content, metadata);

        // 3. Chuyển thành Vector và lưu vào Qdrant
        Embedding embedding = embeddingModel.embed(content).content();
        embeddingStore.add(embedding, segment);
        
        log.info("[Ingestion] Successfully indexed message to Qdrant vector store.");
    }
}
