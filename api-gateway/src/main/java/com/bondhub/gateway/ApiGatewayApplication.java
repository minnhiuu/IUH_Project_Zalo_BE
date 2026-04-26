package com.bondhub.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(exclude = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class
})
@ComponentScan(
        basePackages = { "com.bondhub.gateway", "com.bondhub.common" },
        excludeFilters = {
                // 1. Loại bỏ các class Config gây crash (Cú pháp mảng chuẩn)
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                com.bondhub.common.config.I18nConfig.class,
                                com.bondhub.common.config.ElasticSearchConfiguration.class,
                                com.bondhub.common.publisher.OutboxEventPublisher.class,
                                com.bondhub.common.scheduler.OutboxEventRetryScheduler.class,
                                com.bondhub.common.config.kafka.KafkaTopicConfig.class,
                        }
                )
        }
)
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

}
