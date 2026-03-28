package com.bondhub.aiservice.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class VectorSearchService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public List<String> search(String query, int topK) {
        log.debug("[VectorSearch] Searching for: {}", query);
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(topK)
                            .minScore(0.7)
                            .build()
            ).matches();

            log.info("[VectorSearch] Found {} matches", matches.size());
            return matches.stream()
                    .map(m -> m.embedded().text())
                    .toList();
        } catch (Exception e) {
            log.error("[VectorSearch] Error during search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
