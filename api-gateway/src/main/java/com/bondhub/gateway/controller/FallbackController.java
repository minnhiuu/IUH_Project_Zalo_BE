package com.bondhub.gateway.controller;

import com.bondhub.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/qr-wait")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> qrWaitFallback() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "PENDING");
        
        return Mono.just(ResponseEntity.ok(ApiResponse.success(data)));
    }

    @RequestMapping("/auth-service")
    public Mono<ResponseEntity<ApiResponse<Object>>> authServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, "Authentication Service is currently unavailable. Please try again later.", null)));
    }

    @RequestMapping("/user-service")
    public Mono<ResponseEntity<ApiResponse<Object>>> userServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, "User Service is currently unavailable. Please try again later.", null)));
    }

    @RequestMapping("/message-service")
    public Mono<ResponseEntity<ApiResponse<Object>>> messageServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, "Message Service is currently unavailable. Please try again later.", null)));
    }

    @RequestMapping("/notification-service")
    public Mono<ResponseEntity<ApiResponse<Object>>> notificationServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, "Notification Service is currently unavailable. Please try again later.", null)));
    }
}
