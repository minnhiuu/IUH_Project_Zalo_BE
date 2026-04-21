package com.bondhub.searchservice.service.index.core;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.stats.IndexStats;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;

@Slf4j
public abstract class AbstractBaseElasticsearchService {

    protected final ElasticsearchOperations esOps;
    protected final ElasticsearchClient esClient;
    protected final ElasticsearchProperties esProperties;
    protected final LocalizationUtil localizationUtil;

    protected AbstractBaseElasticsearchService(
            ElasticsearchOperations esOps,
            ElasticsearchClient esClient,
            ElasticsearchProperties esProperties,
            LocalizationUtil localizationUtil) {
        this.esOps = esOps;
        this.esClient = esClient;
        this.esProperties = esProperties;
        this.localizationUtil = localizationUtil;
    }

    public abstract String getAlias();

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
}
