package com.bondhub.notificationservices;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EnableFeignClients
@EnableMongoAuditing
@SpringBootApplication(exclude = {
        ElasticsearchClientAutoConfiguration.class,
        ElasticsearchRestClientAutoConfiguration.class,
        ElasticsearchDataAutoConfiguration.class,
        ElasticsearchRepositoriesAutoConfiguration.class,
        ReactiveElasticsearchClientAutoConfiguration.class,
        ReactiveElasticsearchRepositoriesAutoConfiguration.class
})
@ComponentScan(basePackages = { "com.bondhub.notificationservices", "com.bondhub.common" }, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                com.bondhub.common.config.ElasticSearchConfiguration.class,
                com.bondhub.common.publisher.OutboxEventPublisher.class,
                com.bondhub.common.scheduler.OutboxEventRetryScheduler.class,
        })
})
@EnableMongoRepositories(basePackages = { "com.bondhub.notificationservices.repository",
        "com.bondhub.common.repository" })
public class NotificationServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServicesApplication.class, args);
    }

}
