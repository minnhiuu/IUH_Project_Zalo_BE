package com.bondhub.aiservice.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class QdrantConfig {

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")
    private int qdrantPort;

    @Value("${qdrant.collection-name:bondhub-messages}")
    private String collectionName;

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Value("${openai.embedding-model-name:text-embedding-3-small}")
    private String embeddingModelName;

    @Bean
    public QdrantClient qdrantClient() {
        log.info("Connecting to Qdrant at {}:{}", qdrantHost, qdrantPort);
        return new QdrantClient(
            QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false).build()
        );
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(QdrantClient qdrantClient) {
        return QdrantEmbeddingStore.builder()
                .client(qdrantClient)
                .collectionName(collectionName)
                .build();
    }

    /**
     * text-embedding-3-small: hiệu năng cao, 1536 chiều
     * Tìm kiếm ngữ nghĩa trong Qdrant chính xác hơn Gemini embedding.
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(openAiApiKey)
                .modelName(embeddingModelName)
                .build();
    }
}
