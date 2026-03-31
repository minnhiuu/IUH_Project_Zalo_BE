package com.bondhub.aiservice.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
@Slf4j
@RequiredArgsConstructor
public class QdrantInitializer {

    private final QdrantClient qdrantClient;

    @Value("${qdrant.collection-name:bondhub-messages}")
    private String collectionName;

    // text-embedding-3-small = 1536 chiều
    @Value("${qdrant.embedding-dimension:1536}")
    private int dimension;

    @PostConstruct
    public void init() {
        try {
            // listCollectionsAsync().get() trả về List<String> tên các collection
            boolean exists = qdrantClient.listCollectionsAsync().get()
                    .stream()
                    .anyMatch(name -> name.equals(collectionName));

            if (!exists) {
                log.info("[Qdrant] Collection '{}' not found. Creating with {} dims (Cosine)...", collectionName, dimension);
                qdrantClient.createCollectionAsync(collectionName,
                        VectorParams.newBuilder()
                                .setDistance(Distance.Cosine)
                                .setSize(dimension)
                                .build()
                ).get();
                log.info("[Qdrant] Collection '{}' created successfully.", collectionName);
            } else {
                log.info("[Qdrant] Collection '{}' already exists. Skipping creation.", collectionName);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("[Qdrant] Failed to initialize collection '{}': {}", collectionName, e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
