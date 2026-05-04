package com.bondhub.notificationservices;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableFeignClients
@EnableMongoAuditing
@EnableScheduling
@EnableRetry
@ComponentScan(basePackages = { "com.bondhub.notificationservices", "com.bondhub.common"})
@EnableMongoRepositories(basePackages = { "com.bondhub.notificationservices.repository", "com.bondhub.common.repository" })
public class NotificationServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServicesApplication.class, args);
    }

}
