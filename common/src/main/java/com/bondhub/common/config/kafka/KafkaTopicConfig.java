package com.bondhub.common.config.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class KafkaTopicConfig {

    private final KafkaTopicProperties topicProperties;

    @Bean
    public NewTopic accountRegisteredEvents() {
        return createTopic(topicProperties.getAccountEvents().getRegistered());
    }

    @Bean
    public NewTopic userIndexRequested() {
        return createTopic(topicProperties.getUserEvents().getIndexRequested());
    }

    @Bean
    public NewTopic userIndexDeleted() {
        return createTopic(topicProperties.getUserEvents().getIndexDeleted());
    }

    @Bean
    public NewTopic userIndexRequestedDLQ() {
        return createTopic(topicProperties.getUserEvents().getIndexRequested() + ".dlq");
    }

    @Bean
    public NewTopic userIndexDeletedDLQ() {
        return createTopic(topicProperties.getUserEvents().getIndexDeleted() + ".dlq");
    }

    private NewTopic createTopic(String topicName) {
        log.info("Creating Kafka topic: {}", topicName);
        return TopicBuilder.name(topicName)
                .partitions(3)
                .replicas(1)
                .compact()
                .build();
    }
}
