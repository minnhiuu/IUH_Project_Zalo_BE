package com.bondhub.notificationservices.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConfigurationProperties(prefix = "notification.kafka.topics")
@Getter
@Setter
public class NotificationKafkaTopicConfig {

    private String raw = "noti.raw";
    private String ready = "noti.ready";
    private String cleanup = "noti.cleanup";

    @Bean
    public NewTopic notificationRawTopic() {
        return TopicBuilder.name(raw).partitions(4).replicas(1).build();
    }

    @Bean
    public NewTopic notificationReadyTopic() {
        return TopicBuilder.name(ready).partitions(4).replicas(1).build();
    }

    @Bean
    public NewTopic notificationCleanupTopic() {
        return TopicBuilder.name(cleanup).partitions(4).replicas(1).build();
    }
}
