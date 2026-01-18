package com.bondhub.authservice.controller;

import com.bondhub.authservice.client.UserServiceClient;
import com.bondhub.authservice.dto.auth.request.LoginRequest;
import com.bondhub.authservice.dto.auth.request.RefreshTokenRequest;
import com.bondhub.authservice.dto.auth.request.RegisterRequest;
import com.bondhub.authservice.dto.auth.response.TokenResponse;
import com.bondhub.authservice.service.auth.AuthenticationService;
import com.bondhub.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 * Handles login, registration, and token operations
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthController {

    AuthenticationService authenticationService;

    /**
     * Login endpoint
     *
     * @param request Login credentials
     * @return Token response
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /auth/login - Login request for phone number: {}", request.phoneNumber());
        TokenResponse tokenResponse = authenticationService.login(request);
        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }

    /**
     * Register endpoint
     *
     * @param request Registration details
     * @return Token response
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /auth/register - Registration request for email: {}", request.email());

        TokenResponse tokenResponse = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tokenResponse));
    }

    /**
     * Refresh token endpoint
     *
     * @param request Refresh token
     * @return New access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("POST /auth/refresh - Token refresh request");
        TokenResponse tokenResponse = authenticationService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }

    /**
     * Logout endpoint
     *
     * @param request Logout request
     * @return Success response
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody com.bondhub.authservice.dto.auth.request.LogoutRequest request) {
        log.info("POST /auth/logout - Logout request");
        authenticationService.logout(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Validate token endpoint (for API Gateway)
     *
     * @param token JWT token
     * @return Validation result
     */
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(@RequestParam String token) {
        log.debug("GET /auth/validate - Token validation request");
        boolean isValid = authenticationService.validateToken(token);
        return ResponseEntity.ok(ApiResponse.success(isValid));
    }
}
