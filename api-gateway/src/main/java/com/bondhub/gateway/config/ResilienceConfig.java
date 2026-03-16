package com.bondhub.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for Resilience4j components and health indicators
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResilienceConfig {

    CircuitBreakerRegistry circuitBreakerRegistry;
    RateLimiterRegistry rateLimiterRegistry;
    RetryRegistry retryRegistry;

    /**
     * Health indicator for circuit breakers
     */
    @Bean
    public HealthIndicator circuitBreakerHealthIndicator() {
        return () -> {
            Map<String, Object> details = new HashMap<>();
            
            circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> {
                String name = circuitBreaker.getName();
                var metrics = circuitBreaker.getMetrics();
                var state = circuitBreaker.getState();
                
                Map<String, Object> cbDetails = new HashMap<>();
                cbDetails.put("state", state.toString());
                cbDetails.put("failureRate", String.format("%.2f%%", metrics.getFailureRate()));
                cbDetails.put("slowCallRate", String.format("%.2f%%", metrics.getSlowCallRate()));
                cbDetails.put("bufferedCalls", metrics.getNumberOfBufferedCalls());
                cbDetails.put("failedCalls", metrics.getNumberOfFailedCalls());
                cbDetails.put("successfulCalls", metrics.getNumberOfSuccessfulCalls());
                
                details.put(name, cbDetails);
                
                // Log circuit breaker state changes
                circuitBreaker.getEventPublisher().onStateTransition(event -> 
                    log.warn("Circuit Breaker '{}' state changed from {} to {}", 
                            name, event.getStateTransition().getFromState(), 
                            event.getStateTransition().getToState())
                );
            });

            boolean allHealthy = circuitBreakerRegistry.getAllCircuitBreakers()
                    .stream()
                    .allMatch(cb -> cb.getState() != io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);

            return allHealthy ? 
                    Health.up().withDetails(details).build() : 
                    Health.down().withDetails(details).build();
        };
    }

    /**
     * Health indicator for rate limiters
     */
    @Bean
    public HealthIndicator rateLimiterHealthIndicator() {
        return () -> {
            Map<String, Object> details = new HashMap<>();
            
            rateLimiterRegistry.getAllRateLimiters().forEach(rateLimiter -> {
                String name = rateLimiter.getName();
                var metrics = rateLimiter.getMetrics();
                
                Map<String, Object> rlDetails = new HashMap<>();
                rlDetails.put("availablePermissions", metrics.getAvailablePermissions());
                rlDetails.put("numberOfWaitingThreads", metrics.getNumberOfWaitingThreads());
                
                details.put(name, rlDetails);
            });

            return Health.up().withDetails(details).build();
        };
    }

    /**
     * Health indicator for retry mechanisms
     */
    @Bean
    public HealthIndicator retryHealthIndicator() {
        return () -> {
            Map<String, Object> details = new HashMap<>();
            
            retryRegistry.getAllRetries().forEach(retry -> {
                String name = retry.getName();
                var metrics = retry.getMetrics();
                
                Map<String, Object> retryDetails = new HashMap<>();
                retryDetails.put("successfulCallsWithoutRetry", 
                        metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt());
                retryDetails.put("successfulCallsWithRetry", 
                        metrics.getNumberOfSuccessfulCallsWithRetryAttempt());
                retryDetails.put("failedCallsWithRetry", 
                        metrics.getNumberOfFailedCallsWithRetryAttempt());
                retryDetails.put("failedCallsWithoutRetry", 
                        metrics.getNumberOfFailedCallsWithoutRetryAttempt());
                
                details.put(name, retryDetails);
            });

            return Health.up().withDetails(details).build();
        };
    }
}
