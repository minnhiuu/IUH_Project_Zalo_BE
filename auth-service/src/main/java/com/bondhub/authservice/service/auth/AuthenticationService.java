package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.dto.auth.request.LoginRequest;
import com.bondhub.authservice.dto.auth.request.RefreshTokenRequest;
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
    TokenResponse login(LoginRequest request);

    /**
     * Register new user account and generate tokens
     *
     * @param request Registration request
     * @return Token response with access and refresh tokens
     */
    TokenResponse register(RegisterRequest request);

    /**
     * Refresh access token using refresh token
     *
     * @param request Refresh token request
     * @return Token response with new access token
     */
    TokenResponse refreshToken(RefreshTokenRequest request);

    /**
     * Logout user (invalidate refresh token)
     *
     * @param request Logout request
     */
    void logout(com.bondhub.authservice.dto.auth.request.LogoutRequest request);

    /**
     * Validate JWT token
     *
     * @param token JWT token to validate
     * @return true if valid, false otherwise
     */
    boolean validateToken(String token);
}
