package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.dto.auth.request.LoginRequest;
import com.bondhub.authservice.dto.auth.request.RefreshRequest;
import com.bondhub.authservice.dto.auth.request.RegisterRequest;
import com.bondhub.authservice.dto.auth.response.TokenResponse;

/**
 * Authentication service interface
 */
public interface AuthenticationService {

    /**
     * Authenticate user and generate tokens
     *
     * @param request Login request with email and password
     * @return Token response with access and refresh tokens
     */
    TokenResponse login(LoginRequest request, String userAgent, String ipAddress);

    TokenResponse register(RegisterRequest request);

    TokenResponse refresh(String refreshToken, RefreshRequest request,
            String userAgent, String ipAddress);

    void logout(String refreshToken);

    /**
     * Validate JWT token
     *
     * @param token JWT token to validate
     * @return true if valid, false otherwise
     */
    boolean validateToken(String token);
}
