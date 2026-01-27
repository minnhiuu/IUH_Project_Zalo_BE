package com.bondhub.notificationservices;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableFeignClients
@ComponentScan(basePackages = { "com.bondhub.notificationservices", "com.bondhub.common"})
@EnableMongoRepositories(basePackages = { "com.bondhub.notificationservices.repository", "com.bondhub.common.repository" })
public class NotificationServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServicesApplication.class, args);
    }

}
