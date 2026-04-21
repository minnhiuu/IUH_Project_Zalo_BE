package com.bondhub.searchservice.service.index.core;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.IndicesStatsResponse;
import co.elastic.clients.elasticsearch.indices.stats.IndexStats;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.dto.response.*;
import com.bondhub.searchservice.enums.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
public abstract class AbstractSearchIndexService implements SearchIndexMonitor {

    protected final ElasticsearchOperations esOps;
    protected final ElasticsearchClient esClient;
    protected final ElasticsearchProperties esProperties;
    protected final LocalizationUtil localizationUtil;

    protected AbstractSearchIndexService(
            ElasticsearchOperations esOps,
            ElasticsearchClient esClient,
            ElasticsearchProperties esProperties,
            LocalizationUtil localizationUtil) {
        this.esOps = esOps;
        this.esClient = esClient;
        this.esProperties = esProperties;
        this.localizationUtil = localizationUtil;
    }

    @Override
    public abstract SearchIndexType getType();

    @Override
    public abstract String getAlias();

    @Override
    public abstract Class<?> getIndexClass();

    @Override
    public ElasticsearchHealthResponse getHealth() {
        String alias = getAlias();
        try {
            HealthResponse healthResponse = esClient.cluster().health();
            String currentIndexName = getActualIndexName(alias);
            boolean indexExists = !currentIndexName.equals(alias);

            return ElasticsearchHealthResponse.builder()
                    .status(convertHealthStatus(healthResponse.status()))
                    .clusterName(healthResponse.clusterName())
                    .indexExists(indexExists)
                    .currentIndexName(indexExists ? currentIndexName : null)
                    .aliasName(alias)
                    .build();

        } catch (IOException e) {
            log.error("Failed to get Elasticsearch health for {}: {}", alias, e.getMessage());
            return ElasticsearchHealthResponse.builder()
                    .status(ElasticsearchClusterStatus.UNREACHABLE)
                    .clusterName("unknown")
                    .indexExists(false)
                    .aliasName(alias)
                    .build();
        }
    }

    @Override
    public IndexStatsResponse getStats() {
        String alias = getAlias();
        try {
            String actualIndex = getActualIndexName(alias);

            var exists = esClient.indices().exists(e -> e.index(actualIndex));
            if (!exists.value()) {
                return IndexStatsResponse.builder()
                        .indexName(alias)
                        .documentCount(0L)
                        .primaryStoreSize("0b")
                        .totalStoreSize("0b")
                        .numberOfShards(0)
                        .numberOfReplicas(0)
                        .build();
            }

            IndicesStatsResponse statsResponse = esClient.indices().stats(s -> s.index(actualIndex));
            var indexStats = statsResponse.indices().get(actualIndex);

            if (indexStats == null) {
                return IndexStatsResponse.builder().indexName(actualIndex).documentCount(0L).build();
            }

            var primaries = indexStats.primaries();
            var total = indexStats.total();

            return IndexStatsResponse.builder()
                    .indexName(actualIndex)
                    .documentCount(primaries != null && primaries.docs() != null ? primaries.docs().count() : 0)
                    .primaryStoreSize(formatStoreSize(primaries))
                    .totalStoreSize(formatStoreSize(total))
                    .numberOfShards(statsResponse.shards() != null ? statsResponse.shards().total().intValue() : 0)
                    .numberOfReplicas(0)
                    .build();

        } catch (IOException e) {
            log.error("Failed to get index stats for {}: {}", alias, e.getMessage());
            return IndexStatsResponse.builder().indexName(alias).documentCount(0L).build();
        }
    }

    @Override
    public List<IndexDetailResponse> getAllPhysicalIndexes() {
        String alias = getAlias();
        try {
            String pattern = alias + "*";
            var indicesResponse = esClient.indices().get(g -> g.index(pattern));
            List<String> indexNames = new ArrayList<>(indicesResponse.result().keySet());

            if (indexNames.isEmpty()) return List.of();

            IndicesStatsResponse allStats = esClient.indices().stats(s -> s.index(indexNames));
            String currentIndex = getActualIndexName(alias);

            return indexNames.stream()
                    .map(name -> {
                        var stats = allStats.indices().get(name);
                        long docCount = (stats != null && stats.primaries() != null && stats.primaries().docs() != null)
                                ? stats.primaries().docs().count() : 0;

                        return new IndexDetailResponse(
                                name,
                                parseTimestampFromIndexName(name),
                                docCount,
                                formatStoreSize(stats != null ? stats.primaries() : null),
                                name.equals(currentIndex) ? IndexStatus.ACTIVE : IndexStatus.STANDBY
                        );
                    })
                    .sorted(Comparator.comparing(IndexDetailResponse::createdAt).reversed())
                    .toList();
        } catch (IOException e) {
            log.error("Failed to get physical indexes for {}: {}", alias, e.getMessage());
            return List.of();
        }
    }

    // ========== Helpers ==========

    protected String getActualIndexName(String alias) {
        try {
            GetAliasResponse aliasResponse = esClient.indices().getAlias(g -> g.name(alias));
            return aliasResponse.result().keySet().stream()
                    .filter(index -> !index.startsWith("."))
                    .sorted(Comparator.reverseOrder())
                    .findFirst().orElse(alias);
        } catch (Exception e) {
            return alias;
        }
    }

    protected LocalDateTime parseTimestampFromIndexName(String indexName) {
        try {
            String[] parts = indexName.split("_");
            long timestamp = Long.parseLong(parts[parts.length - 1]);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    protected String formatStoreSize(IndexStats stats) {
        if (stats == null || stats.store() == null) return "0b";
        String size = stats.store().size();
        if (size != null && !size.isEmpty()) return size;
        return formatByteSize(stats.store().sizeInBytes());
    }

    protected String formatByteSize(Long bytes) {
        if (bytes == null || bytes <= 0) return "0b";
        final String[] units = new String[]{"b", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + units[digitGroups];
    }

    private ElasticsearchClusterStatus convertHealthStatus(HealthStatus status) {
        if (status == null) return ElasticsearchClusterStatus.UNKNOWN;
        return switch (status) {
            case Green -> ElasticsearchClusterStatus.GREEN;
            case Yellow -> ElasticsearchClusterStatus.YELLOW;
            case Red -> ElasticsearchClusterStatus.RED;
            default -> ElasticsearchClusterStatus.UNKNOWN;
        };
    }
}
