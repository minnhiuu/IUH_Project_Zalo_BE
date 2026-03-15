package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.dto.auth.request.ChangePasswordRequest;
import com.bondhub.authservice.dto.auth.request.ForgotPasswordRequest;
import com.bondhub.authservice.dto.auth.request.LoginRequest;
import com.bondhub.authservice.dto.auth.request.RefreshRequest;
import com.bondhub.authservice.dto.auth.request.RegisterInitRequest;
import com.bondhub.authservice.dto.auth.request.RegisterRequest;
import com.bondhub.authservice.dto.auth.request.RegisterVerifyRequest;
import com.bondhub.authservice.dto.auth.request.ResetPasswordRequest;
import com.bondhub.authservice.dto.auth.response.ForgotPasswordResponse;
import com.bondhub.authservice.dto.auth.response.RegisterInitResponse;
import com.bondhub.authservice.dto.auth.response.TokenResponse;

/**
 * Authentication service interface
 */
public interface AuthenticationService {

    /**
     * Authenticate user and generate tokens
     *
     * @param request   Login request with email and password
     * @param userAgent User Agent string from the request header
     * @param ipAddress IP address of the client
     * @return Token response with access and refresh tokens
     */
    TokenResponse login(LoginRequest request, String userAgent, String ipAddress);

    /**
     * Register a new user (Single-step registration - OLD/DEPRECATED)
     *
     * @param request Registration request with email, password, etc.
     * @return Token response with access token
     */
    TokenResponse register(RegisterRequest request);

    /**
     * Step 1: Initiate registration flow
     * <p>
     * Validates input, generates OTP, and sends it via email.
     * Stores pending registration data in Redis with a TTL.
     * </p>
     *
     * @param request Registration initiation request
     * @return Response confirming OTP sent
     */
    RegisterInitResponse initiateRegistration(RegisterInitRequest request);

    /**
     * Step 2: Verify OTP and complete registration
     * <p>
     * Validates OTP, creates user account (verified), and generates tokens
     * (auto-login).
     * </p>
     *
     * @param request   Verification request with OTP
     * @param userAgent User Agent string
     * @param ipAddress IP address of the client
     * @return Token response with access and refresh tokens
     */
    TokenResponse verifyAndCompleteRegistration(RegisterVerifyRequest request, String userAgent, String ipAddress);

    /**
     * Initiate password reset flow (Step 1)
     * <p>
     * Generates OTP and sends it to the user's email if the account exists.
     * </p>
     *
     * @param request Password reset initiation request
     * @return Response indicating success
     */
    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);

    /**
     * Complete password reset flow (Step 2)
     * <p>
     * Validates OTP, updates password, and generates new tokens (auto-login).
     * </p>
     *
     * @param request   Reset request with OTP and new password
     * @param userAgent User Agent string
     * @param ipAddress IP address of the client
     * @return Token response with new tokens
     */
    TokenResponse resetPassword(ResetPasswordRequest request, String userAgent, String ipAddress);

    /**
     * Change password for authenticated user
     * <p>
     * Validates current password and updates to new password.
     * Does not generate new tokens - user remains authenticated.
     * </p>
     *
     * @param request Change password request with old and new passwords
     */
    void changePassword(ChangePasswordRequest request);

    /**
     * Refresh access token using a valid refresh token
     *
     * @param refreshToken The refresh token string
     * @param request      Refresh request containing device ID (for binding check)
     * @param userAgent    User Agent string
     * @param ipAddress    IP address of the client
     * @return New Token response with rotated tokens
     */
    TokenResponse refresh(String refreshToken, RefreshRequest request,
            String userAgent, String ipAddress);

    /**
     * Logout user
     * <p>
     * Blacklists the current access token and revokes the refresh token session.
     * </p>
     *
     * @param refreshToken The refresh token to revoke
     */
    void logout(String refreshToken);

    /**
     * Logout from all other devices except the current one
     *
     * @param refreshToken The refresh token of the current session
     */
    void logoutAllOtherDevices(String refreshToken);

    /**
     * Logout a specific device
     *
     * @param targetSessionId Session ID of the device to logout
     * @param refreshToken    Current user's refresh token (for authorization)
     */
    void logoutDevice(String targetSessionId, String refreshToken);

    /**
     * Validate JWT token
     *
     * @param token JWT token to validate
     * @return true if valid, false otherwise
     */
    boolean validateToken(String token);
}
