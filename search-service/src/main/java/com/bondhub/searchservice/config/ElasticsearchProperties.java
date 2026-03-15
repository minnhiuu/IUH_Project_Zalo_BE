package com.bondhub.searchservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "elasticsearch")
@Data
public class ElasticsearchProperties {

    private SyncConfig sync = new SyncConfig();
    private IndexConfig index = new IndexConfig();
    private String userAlias = "users";
    private MonitoringConfig monitoring = new MonitoringConfig();

    @Data
    public static class SyncConfig {
        private int batchSize = 500;
        private int maxRetry = 3;
        private int parallelism = 0; // 0 = auto-detect CPU cores
    }

    @Data
    public static class IndexConfig {
        private int realtimeThreads = 2;
        private int asyncTimeoutSeconds = 30;
        private int retainIndexCount = 3;
    }

    @Data
    public static class MonitoringConfig {
        private boolean enabled = true;
        private boolean logSlowOperations = true;
        private int slowOperationThresholdMs = 1000;
    }
}
