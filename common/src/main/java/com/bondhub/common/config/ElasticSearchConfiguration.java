package com.bondhub.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.config.EnableElasticsearchAuditing;
@Configuration
@EnableElasticsearchAuditing
public class ElasticSearchConfiguration extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris}")
    private String elasticSearchUrl;

    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo(elasticSearchUrl)
                .build();
    }
}
