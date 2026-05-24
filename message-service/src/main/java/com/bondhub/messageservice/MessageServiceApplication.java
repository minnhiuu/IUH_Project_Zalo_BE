package com.bondhub.messageservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableMongoAuditing
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
@ComponentScan(basePackages = { "com.bondhub.messageservice", "com.bondhub.common"})
@EnableMongoRepositories(basePackages = { "com.bondhub.messageservice.repository", "com.bondhub.common.repository" })
public class MessageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageServiceApplication.class, args);
    }

}
