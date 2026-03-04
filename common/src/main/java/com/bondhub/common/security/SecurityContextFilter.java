package com.bondhub.common.security;

import com.bondhub.common.enums.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityContextFilter extends OncePerRequestFilter {

    private static final String HEADER_ACCOUNT_ID = "X-Account-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_USER_JTI = "X-JWT-Id";
    private static final String HEADER_REMAINING_TTL = "X-Remaining-TTL";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String accountId = request.getHeader(HEADER_ACCOUNT_ID);
        String userId = request.getHeader(HEADER_USER_ID);
        String email = request.getHeader(HEADER_USER_EMAIL);
        String rolesHeader = request.getHeader(HEADER_USER_ROLES);
        String jti = request.getHeader(HEADER_USER_JTI);
        Long remainingTTL = request.getHeader(HEADER_REMAINING_TTL) != null
                ? Long.parseLong(request.getHeader(HEADER_REMAINING_TTL))
                : null;

        if (accountId != null && email != null) {
            try {
                List<GrantedAuthority> authorities = parseRoles(rolesHeader);

                UserPrincipal userPrincipal = new UserPrincipal(accountId, userId, email, jti, remainingTTL,
                        authorities);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal,
                        null,
                        authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Security context set for user: {} ({}) and account: {}", email, userId, accountId);

            } catch (Exception e) {
                log.error("Error setting security context: {}", e.getMessage());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
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
