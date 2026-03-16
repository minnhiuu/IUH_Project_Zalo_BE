package com.bondhub.searchservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = {"com.bondhub.searchservice", "com.bondhub.common"})
@EnableFeignClients
@EnableMongoRepositories(basePackages = {"com.bondhub.common.repository", "com.bondhub.searchservice.repository"})
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }

}
