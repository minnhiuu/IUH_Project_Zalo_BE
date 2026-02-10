package com.bondhub.gateway.exception;

import com.bondhub.common.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Global exception handler for API Gateway
 * Handles circuit breaker, rate limiting, and other gateway-specific errors
 */
@Slf4j
@Order(-2)
@Configuration
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Object> apiResponse;
        HttpStatus status;

        if (ex instanceof CallNotPermittedException) {
            // Circuit breaker is open
            log.error("Circuit breaker is open for request: {} {}", 
                    exchange.getRequest().getMethod(), 
                    exchange.getRequest().getPath());
            
            status = HttpStatus.SERVICE_UNAVAILABLE;
            Map<String, String> details = createErrorDetails(
                    "Circuit Breaker Open",
                    "The service is temporarily unavailable due to repeated failures. Please try again later.",
                    exchange
            );
            apiResponse = ApiResponse.error(503, "Service Circuit Breaker Open", details);
            
        } else if (ex instanceof RequestNotPermitted) {
            // Rate limit exceeded
            log.warn("Rate limit exceeded for request: {} {}", 
                    exchange.getRequest().getMethod(), 
                    exchange.getRequest().getPath());
            
            status = HttpStatus.TOO_MANY_REQUESTS;
            Map<String, String> details = createErrorDetails(
                    "Rate Limit Exceeded",
                    "Too many requests. Please slow down and try again later.",
                    exchange
            );
            apiResponse = ApiResponse.error(429, "Too Many Requests", details);
            
        } else if (ex instanceof TimeoutException) {
            // Request timeout
            log.error("Request timeout for: {} {}", 
                    exchange.getRequest().getMethod(), 
                    exchange.getRequest().getPath());
            
            status = HttpStatus.GATEWAY_TIMEOUT;
            Map<String, String> details = createErrorDetails(
                    "Request Timeout",
                    "The request took too long to complete. Please try again.",
                    exchange
            );
            apiResponse = ApiResponse.error(504, "Gateway Timeout", details);
            
        } else if (ex instanceof ResponseStatusException responseStatusException) {
            // HTTP status exceptions
            status = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
            Map<String, String> details = createErrorDetails(
                    "HTTP Error",
                    responseStatusException.getReason() != null ? 
                            responseStatusException.getReason() : "An error occurred processing your request",
                    exchange
            );
            apiResponse = ApiResponse.error(status.value(), status.getReasonPhrase(), details);
            
        } else {
            // Generic error
            log.error("Unhandled error in gateway for request: {} {}", 
                    exchange.getRequest().getMethod(), 
                    exchange.getRequest().getPath(), ex);
            
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            Map<String, String> details = createErrorDetails(
                    "Internal Server Error",
                    "An unexpected error occurred. Please try again later.",
                    exchange
            );
            apiResponse = ApiResponse.error(500, "Internal Server Error", details);
        }

        response.setStatusCode(status);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(apiResponse).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            log.error("Error serializing error response", e);
            bytes = "{\"success\":false,\"message\":\"Internal server error\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private Map<String, String> createErrorDetails(String errorType, String message, ServerWebExchange exchange) {
        Map<String, String> details = new HashMap<>();
        details.put("errorType", errorType);
        details.put("message", message);
        details.put("timestamp", LocalDateTime.now().format(FORMATTER));
        details.put("path", exchange.getRequest().getPath().toString());
        details.put("method", exchange.getRequest().getMethod().toString());
        return details;
    }
}
