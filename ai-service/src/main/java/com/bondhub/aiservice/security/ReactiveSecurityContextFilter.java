package com.bondhub.aiservice.security;

import com.bondhub.common.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reactive Security Context Filter
 * Extracts user information from request headers set by the API Gateway
 */
@Component
@Slf4j
public class ReactiveSecurityContextFilter implements WebFilter {

    private static final String HEADER_ACCOUNT_ID = "X-Account-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_USER_JTI = "X-JWT-Id";
    private static final String HEADER_REMAINING_TTL = "X-Remaining-TTL";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String accountId = exchange.getRequest().getHeaders().getFirst(HEADER_ACCOUNT_ID);
        String userId = exchange.getRequest().getHeaders().getFirst(HEADER_USER_ID);
        String email = exchange.getRequest().getHeaders().getFirst(HEADER_USER_EMAIL);
        String rolesHeader = exchange.getRequest().getHeaders().getFirst(HEADER_USER_ROLES);
        String jti = exchange.getRequest().getHeaders().getFirst(HEADER_USER_JTI);
        String ttlHeader = exchange.getRequest().getHeaders().getFirst(HEADER_REMAINING_TTL);
        Long remainingTTL = ttlHeader != null ? Long.parseLong(ttlHeader) : null;

        if (accountId != null && email != null) {
            try {
                List<GrantedAuthority> authorities = parseRoles(rolesHeader);

                UserPrincipal userPrincipal = new UserPrincipal(accountId, userId, email, jti, remainingTTL, authorities);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal,
                        null,
                        authorities);

                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

            } catch (Exception e) {
                log.error("Error setting security context: {}", e.getMessage());
            }
        }

        return chain.filter(exchange);
    }

    private List<GrantedAuthority> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.trim().isEmpty()) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> {
                    String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                    return new SimpleGrantedAuthority(roleWithPrefix);
                })
                .collect(Collectors.toList());
    }
}
