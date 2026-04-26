package com.bondhub.fileservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableFeignClients
@EnableMongoAuditing
@ComponentScan(basePackages = { "com.bondhub.fileservice", "com.bondhub.common" },
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                com.bondhub.common.config.ElasticSearchConfiguration.class,
                                com.bondhub.common.config.kafka.KafkaTopicConfig.class,
                        }
                )
        }
)
@EnableMongoRepositories(basePackages = { "com.bondhub.common.repository" })
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }

}
