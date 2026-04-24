package com.bondhub.searchservice.service.index.core;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.IndicesStatsResponse;
import co.elastic.clients.elasticsearch.indices.stats.IndexStats;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.dto.request.FailedEventFilter;
import com.bondhub.searchservice.dto.response.*;
import com.bondhub.searchservice.enums.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import com.bondhub.searchservice.service.failevent.FailedEventService;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
public abstract class AbstractMonitorService extends AbstractBaseElasticsearchService implements SearchIndexMonitor {

    protected final FailedEventService failedEventService;

    protected AbstractMonitorService(
            ElasticsearchOperations esOps,
            ElasticsearchClient esClient,
            ElasticsearchProperties esProperties,
            LocalizationUtil localizationUtil,
            FailedEventService failedEventService) {
        super(esOps, esClient, esProperties, localizationUtil);
        this.failedEventService = failedEventService;
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

    @Override
    public long getFailedEventsCount() {
        return failedEventService.countEventsByResolvedAndTopics(false, getType().getTopics());
    }

    @Override
    public PageResponse<List<FailedEventResponse>> getFailedEvents(int page, int size) {
        FailedEventFilter filter = FailedEventFilter.builder()
                .resolved(false)
                .type(getType())
                .page(page)
                .size(size)
                .build();
        return failedEventService.getEventsPaged(filter);
    }

    private ElasticsearchClusterStatus convertHealthStatus(HealthStatus status) {
        if (status == null) return ElasticsearchClusterStatus.UNKNOWN;
        switch (status) {
            case Green: return ElasticsearchClusterStatus.GREEN;
            case Yellow: return ElasticsearchClusterStatus.YELLOW;
            case Red: return ElasticsearchClusterStatus.RED;
            default: return ElasticsearchClusterStatus.UNKNOWN;
        }
    }
}
