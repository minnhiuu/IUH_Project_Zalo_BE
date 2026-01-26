package com.bondhub.authservice.util;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.authservice.model.Account;
import com.bondhub.authservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SecurityUtil {

    private final AccountRepository accountRepository;

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";

    public Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }

        throw new AppException(ErrorCode.AUTH_UNAUTHENTICATED);
    }

    public String getCurrentAccountId() {
        return getCurrentJwt().getSubject();
    }

    public String getCurrentEmail() {

        Jwt jwt = getCurrentJwt();
        return jwt.getClaimAsString(CLAIM_EMAIL);
    }

    public String getCurrentJwtId() {
        Jwt jwt = getCurrentJwt();
        return jwt.getId();
    }

    @SuppressWarnings("unchecked")
    public List<String> getCurrentRoles() {
        Jwt jwt = getCurrentJwt();
        Object roles = jwt.getClaim(CLAIM_ROLES);

        if (roles == null) {
            return Collections.emptyList();
        }

        if (roles instanceof List) {
            return (List<String>) roles;
        }

        return Collections.emptyList();
    }

    public String getCurrentRole() {
        List<String> roles = getCurrentRoles();
        if (roles.isEmpty()) {
            return null;
        }
        String role = roles.get(0);
        return role.startsWith("ROLE_") ? role.substring(5) : role;
    }

    public List<String> getCurrentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return Collections.emptyList();
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    public long getRemainingTtlSeconds() {
        Jwt jwt = getCurrentJwt();
        if (jwt.getExpiresAt() == null) {
            return 0;
        }
        long remainingMs = jwt.getExpiresAt().toEpochMilli() - System.currentTimeMillis();
        return Math.max(0, remainingMs / 1000);
    }

    public boolean hasRole(String role) {
        String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return getCurrentAuthorities().contains(roleWithPrefix);
    }

    public boolean hasAnyRole(String... roles) {
        List<String> authorities = getCurrentAuthorities();
        for (String role : roles) {
            String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            if (authorities.contains(roleWithPrefix)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication instanceof JwtAuthenticationToken;
    }

    public Account getCurrentAccount() {
        String userId = getCurrentAccountId();
        return accountRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND));
    }
}
