package com.bondhub.gateway.controller;

import com.bondhub.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fallback controller to handle service unavailability and circuit breaker fallbacks.
 * Provides graceful degradation when downstream services are unavailable.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
@RequiredArgsConstructor
public class FallbackController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final MessageSource messageSource;

    @RequestMapping("/qr-wait")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> qrWaitFallback(ServerHttpRequest request) {
        log.warn("QR Wait service fallback triggered for request: {} {}", 
                request.getMethod(), request.getPath());
        
        Locale locale = getLocaleFromRequest(request);
        
        Map<String, Object> data = new HashMap<>();
        data.put("status", messageSource.getMessage("fallback.qr.wait.status", null, locale));
        data.put("message", messageSource.getMessage("fallback.qr.wait.message", null, locale));
        data.put("timestamp", LocalDateTime.now().format(FORMATTER));
        
        return Mono.just(ResponseEntity.ok(ApiResponse.success(data)));
    }

    @RequestMapping("/auth-service")
    public Mono<ResponseEntity<ApiResponse<Object>>> authServiceFallback(ServerHttpRequest request) {
        log.error("Auth service fallback triggered for request: {} {}", 
                request.getMethod(), request.getPath());
        
        Locale locale = getLocaleFromRequest(request);
        
        Map<String, String> errorDetails = createErrorDetails(
                messageSource.getMessage("fallback.auth.service.name", null, locale),
                messageSource.getMessage("fallback.auth.service.message", null, locale),
                request,
                locale
        );
        
        String errorTitle = messageSource.getMessage("fallback.auth.service.unavailable", null, locale);
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, errorTitle, errorDetails)));
    }

    @RequestMapping("/user-service")
    public Mono<ResponseEntity<ApiResponse<Object>>> userServiceFallback(ServerHttpRequest request) {
        log.error("User service fallback triggered for request: {} {}", 
                request.getMethod(), request.getPath());
        
        Locale locale = getLocaleFromRequest(request);
        
        Map<String, String> errorDetails = createErrorDetails(
                messageSource.getMessage("fallback.user.service.name", null, locale),
                messageSource.getMessage("fallback.user.service.message", null, locale),
                request,
                locale
        );
        
        String errorTitle = messageSource.getMessage("fallback.user.service.unavailable", null, locale);
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, errorTitle, errorDetails)));
    }

    @RequestMapping("/message-service")
    public Mono<ResponseEntity<ApiResponse<Object>>> messageServiceFallback(ServerHttpRequest request) {
        log.error("Message service fallback triggered for request: {} {}", 
                request.getMethod(), request.getPath());
        
        Locale locale = getLocaleFromRequest(request);
        
        Map<String, String> errorDetails = createErrorDetails(
                messageSource.getMessage("fallback.message.service.name", null, locale),
                messageSource.getMessage("fallback.message.service.message", null, locale),
                request,
                locale
        );
        
        String errorTitle = messageSource.getMessage("fallback.message.service.unavailable", null, locale);
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, errorTitle, errorDetails)));
    }

    @RequestMapping("/notification-service")
    public Mono<ResponseEntity<ApiResponse<Object>>> notificationServiceFallback(ServerHttpRequest request) {
        log.error("Notification service fallback triggered for request: {} {}", 
                request.getMethod(), request.getPath());
        
        Locale locale = getLocaleFromRequest(request);
        
        Map<String, String> errorDetails = createErrorDetails(
                messageSource.getMessage("fallback.notification.service.name", null, locale),
                messageSource.getMessage("fallback.notification.service.message", null, locale),
                request,
                locale
        );
        
        String errorTitle = messageSource.getMessage("fallback.notification.service.unavailable", null, locale);
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, errorTitle, errorDetails)));
    }

    /**
     * Fallback for general gateway errors
     */
    @RequestMapping("/general")
    public Mono<ResponseEntity<ApiResponse<Object>>> generalFallback(ServerHttpRequest request) {
        log.error("General fallback triggered for request: {} {}", 
                request.getMethod(), request.getPath());
        
        Locale locale = getLocaleFromRequest(request);
        
        Map<String, String> errorDetails = createErrorDetails(
                messageSource.getMessage("fallback.general.service.name", null, locale),
                messageSource.getMessage("fallback.general.message", null, locale),
                request,
                locale
        );
        
        String errorTitle = messageSource.getMessage("fallback.general.unavailable", null, locale);
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, errorTitle, errorDetails)));
    }

    /**
     * Fallback for timeout errors
     */
    @RequestMapping("/timeout")
    public Mono<ResponseEntity<ApiResponse<Object>>> timeoutFallback(ServerHttpRequest request) {
        log.error("Timeout fallback triggered for request: {} {}", 
                request.getMethod(), request.getPath());
        
        Locale locale = getLocaleFromRequest(request);
        
        Map<String, String> errorDetails = new HashMap<>();
        errorDetails.put("service", messageSource.getMessage("fallback.timeout.service.name", null, locale));
        errorDetails.put("reason", messageSource.getMessage("fallback.timeout.reason", null, locale));
        errorDetails.put("message", messageSource.getMessage("fallback.timeout.message", null, locale));
        errorDetails.put("timestamp", LocalDateTime.now().format(FORMATTER));
        errorDetails.put("path", request.getPath().toString());
        
        String errorTitle = messageSource.getMessage("fallback.timeout.error", null, locale);
        
        return Mono.just(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.error(504, errorTitle, errorDetails)));
    }

    private Map<String, String> createErrorDetails(String serviceName, String message, ServerHttpRequest request, Locale locale) {
        Map<String, String> details = new HashMap<>();
        details.put("service", serviceName);
        details.put("message", message);
        details.put("timestamp", LocalDateTime.now().format(FORMATTER));
        details.put("path", request.getPath().toString());
        details.put("suggestion", messageSource.getMessage("fallback.suggestion", null, locale));
        return details;
    }

    /**
     * Extract locale from request Accept-Language header
     * Defaults to Vietnamese if header is not present
     */
    private Locale getLocaleFromRequest(ServerHttpRequest request) {
        return request.getHeaders().getAcceptLanguageAsLocales()
                .stream()
                .findFirst()
                .orElse(new Locale("vi"));
    }
}
