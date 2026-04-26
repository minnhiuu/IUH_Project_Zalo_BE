package com.bondhub.searchservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(
        scanBasePackages = {"com.bondhub.searchservice", "com.bondhub.common"},
        exclude = {
                ReactiveElasticsearchRepositoriesAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class
        }
)
@EnableFeignClients
@EnableMongoRepositories(basePackages = {
        "com.bondhub.common.repository",
        "com.bondhub.searchservice.repository.mongo"
})
@EnableElasticsearchRepositories(basePackages = {
        "com.bondhub.searchservice.repository.elastic"
})
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }

}
