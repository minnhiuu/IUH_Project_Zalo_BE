package com.bondhub.common.utils;

import com.bondhub.common.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for service layer security operations
 */
public class ServiceSecurityUtils {

    private ServiceSecurityUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Get the current authenticated user's account ID from security context
     *
     * @return the account ID of the authenticated user
     * @throws ClassCastException if the principal is not a UserPrincipal
     */
    public static String getCurrentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return principal.getAccountId();
    }

    /**
     * Get the current authenticated user's profile ID from security context
     *
     * @return the profile ID of the authenticated user
     * @throws ClassCastException if the principal is not a UserPrincipal
     */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return principal.getUserId();
    }

    /**
     * Get the current UserPrincipal from security context
     *
     * @return the UserPrincipal of the authenticated user
     * @throws ClassCastException if the principal is not a UserPrincipal
     */
    public static UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (UserPrincipal) authentication.getPrincipal();
    }
}
