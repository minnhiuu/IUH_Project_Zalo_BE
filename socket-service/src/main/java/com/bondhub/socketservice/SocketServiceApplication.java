package com.bondhub.socketservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
        "com.bondhub.socketservice",
        "com.bondhub.common.security",
        "com.bondhub.common.utils",
        "com.bondhub.common.dto",
        "com.bondhub.common.enums",
        "com.bondhub.common.config",
        "com.bondhub.common.exception"
})
@EnableDiscoveryClient
@EnableFeignClients
public class SocketServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SocketServiceApplication.class, args);
    }
}
