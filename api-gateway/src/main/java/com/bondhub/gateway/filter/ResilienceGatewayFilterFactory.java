package com.bondhub.gateway.filter;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Custom Gateway Filter Factory for applying resilience patterns
 * Applies circuit breaker and retry logic to routes
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResilienceGatewayFilterFactory extends 
        AbstractGatewayFilterFactory<ResilienceGatewayFilterFactory.Config> {

    CircuitBreakerRegistry circuitBreakerRegistry;
    RetryRegistry retryRegistry;

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String serviceName = config.getServiceName();
            
            log.debug("Applying resilience patterns for service: {}", serviceName);

            return chain.filter(exchange)
                    .transformDeferred(CircuitBreakerOperator.of(
                            circuitBreakerRegistry.circuitBreaker(serviceName + "CircuitBreaker")))
                    .transformDeferred(RetryOperator.of(
                            retryRegistry.retry(serviceName + "Retry")))
                    .onErrorResume(error -> {
                        log.error("Error in resilience filter for service {}: {}", 
                                serviceName, error.getMessage());
                        return Mono.error(error);
                    });
        };
    }

    public static class Config {
        private String serviceName;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
    }
}
