package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.security.UserPrincipal;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Test Controller for Security Context Filter
 * Demonstrates @PreAuthorize and security context functionality
 */
@RestController
@RequestMapping("/users/test/security")
@Slf4j
public class SecurityTestController {

    /**
     * Public endpoint - accessible to anyone
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publicEndpoint() {

        log.info("Huyen beo");

        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is a public endpoint - no authentication required");
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Authenticated endpoint - requires any authenticated user
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/authenticated")
    public ResponseEntity<ApiResponse<UserInfo>> authenticatedEndpoint() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        UserInfo userInfo = new UserInfo(
                principal.getId(),
                principal.getEmail(),
                principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()),
                "Access granted - you are authenticated!");

        log.info("Authenticated user accessed endpoint: {}", principal.getEmail());

        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    /**
     * Admin only endpoint - requires ADMIN role
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin-only")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminOnlyEndpoint() {
        UserPrincipal principal = getCurrentUser();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Welcome, Admin!");
        response.put("adminUser", principal.getEmail());
        response.put("timestamp", LocalDateTime.now());

        log.info("Admin user accessed admin endpoint: {}", principal.getEmail());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * User or Admin endpoint - requires USER or ADMIN role
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/user-or-admin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> userOrAdminEndpoint() {
        UserPrincipal principal = getCurrentUser();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Access granted to USER or ADMIN");
        response.put("user", principal.getEmail());
        response.put("roles", principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get current user information
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/whoami")
    public ResponseEntity<ApiResponse<UserInfo>> whoAmI() {
        UserPrincipal principal = getCurrentUser();

        UserInfo userInfo = new UserInfo(
                principal.getId(),
                principal.getEmail(),
                principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()),
                "Current user information retrieved successfully");

        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    /**
     * Verify user can only access their own data
     * This demonstrates path variable authorization
     */
    @PreAuthorize("#userId == authentication.principal.id")
    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserProfile(@PathVariable String userId) {
        UserPrincipal principal = getCurrentUser();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Profile access granted");
        response.put("userId", userId);
        response.put("requestedBy", principal.getEmail());
        response.put("note", "You can only access your own profile");

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Complex authorization - user can access their own data OR admin can access
     * anyone's data
     */
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteUser(@PathVariable String userId) {
        UserPrincipal principal = getCurrentUser();

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User deletion authorized");
        response.put("targetUserId", userId);
        response.put("deletedBy", principal.getEmail());
        response.put("isAdminAction", isAdmin);
        response.put("note", "This is a test endpoint - no actual deletion occurs");

        log.warn("User deletion simulated - userId: {}, deletedBy: {}, isAdmin: {}",
                userId, principal.getEmail(), isAdmin);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Test endpoint to see all security headers
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/headers")
    public ResponseEntity<ApiResponse<Map<String, String>>> checkHeaders(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {

        Map<String, String> headers = new HashMap<>();
        headers.put("X-User-Id", userId);
        headers.put("X-User-Email", email);
        headers.put("X-User-Roles", roles);

        UserPrincipal principal = getCurrentUser();
        headers.put("SecurityContext-UserId", principal.getId());
        headers.put("SecurityContext-Email", principal.getEmail());

        return ResponseEntity.ok(ApiResponse.success(headers));
    }

    /**
     * Helper method to get current user from security context
     */
    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (UserPrincipal) authentication.getPrincipal();
    }

    /**
     * User information DTO
     */
    @Data
    public static class UserInfo {
        private final String userId;
        private final String email;
        private final java.util.List<String> roles;
        private final String message;
    }
}
