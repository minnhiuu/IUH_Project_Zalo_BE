package com.bondhub.gateway.filter;

import com.bondhub.common.config.SecurityPaths;
import com.bondhub.common.utils.JwtUtil;
import com.bondhub.gateway.service.TokenValidationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    JwtUtil jwtUtil;
    TokenValidationService tokenValidationService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        log.debug("Processing request: {} {}", request.getMethod(), path);

        if (SecurityPaths.isPublicPath(path)) {
            log.debug("Public path detected, skipping authentication: {}", path);
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        return tokenValidationService.validateToken(token)
                .flatMap(isValid -> {
                    if (!isValid) {
                        log.warn("Token validation failed for path: {}", path);
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    try {
                        String userId = jwtUtil.extractUserId(token);
                        String email = jwtUtil.extractEmail(token);
                        String role = jwtUtil.extractRole(token);
                        String jti = jwtUtil.extractJti(token);
                        String remainingTTL = String.valueOf(jwtUtil.getRemainingTtl(token));

                        ServerHttpRequest modifiedRequest = request.mutate()
                                .header("X-User-Id", userId)
                                .header("X-User-Email", email)
                                .header("X-User-Roles", role)
                                .header("X-JWT-Id", jti)
                                .header("X-Remaining-TTL", remainingTTL)
                                .build();

                        log.debug("Authentication successful for user: {} ({})", email, userId);

                        return chain.filter(exchange.mutate().request(modifiedRequest).build());

                    } catch (Exception e) {
                        log.error("Error extracting token claims: {}", e.getMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error during token validation: {}", e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }

    @Override
    public int getOrder() {
        return -100; // High priority - run before other filters
    }
}
