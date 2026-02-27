package com.bondhub.common.utils;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityUtil {

    private UserPrincipal getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal) {
            return (UserPrincipal) principal;
        }

        throw new AppException(ErrorCode.AUTH_UNAUTHENTICATED);
    }

    public String getCurrentAccountId() {
        return getCurrentUserPrincipal().getAccountId();
    }

    public String getCurrentUserId() {
        return getCurrentUserPrincipal().getUserId();
    }

    public String getCurrentEmail() {
        return getCurrentUserPrincipal().getEmail();
    }

    public String getCurrentJwtId() {
        return getCurrentUserPrincipal().getJti();
    }

    public List<String> getCurrentRoles() {
        UserPrincipal userPrincipal = getCurrentUserPrincipal();
        return userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
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
        return getCurrentUserPrincipal().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    public long getRemainingTtlSeconds() {
        return getCurrentUserPrincipal().getRemainingTTL();
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
                && authentication.isAuthenticated();
    }
}
