package com.bondhub.aiservice.service.retrival.vectorsearch;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
@Slf4j
@RequiredArgsConstructor
public class VectorSearchServiceImpl implements VectorSearchService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Override
    public List<String> search(String query, String conversationId, int topK) {
        log.debug("[VectorSearch] Searching for: '{}' in conversation: {}", query, conversationId);
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            if (queryEmbedding == null || queryEmbedding.vector().length == 0) {
                log.warn("[VectorSearch] Empty embedding generated for query: '{}'", query);
                return Collections.emptyList();
            }

            log.debug("[VectorSearch] Query vector size: {}", queryEmbedding.vector().length);

            Filter conversationFilter = metadataKey("chat_id").isEqualTo(conversationId);

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .filter(conversationFilter)
                            .maxResults(topK)
                            .minScore(0.7)
                            .build()
            ).matches();

            log.info("[VectorSearch] Found {} matches in conversation: {}", matches.size(), conversationId);
            return matches.stream()
                    .filter(m -> m.embedded() != null && m.embedded().text() != null)
                    .map(m -> m.embedded().text())
                    .toList();

        } catch (Exception e) {
            log.error("[VectorSearch] Error during search for conversation {}: {}", conversationId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
