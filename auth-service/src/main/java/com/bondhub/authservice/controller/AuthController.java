package com.bondhub.authservice.controller;

import com.bondhub.authservice.dto.auth.request.*;
import com.bondhub.authservice.dto.auth.response.ForgotPasswordResponse;
import com.bondhub.authservice.dto.auth.response.RegisterInitResponse;
import com.bondhub.authservice.dto.auth.response.TokenResponse;
import com.bondhub.authservice.service.auth.AuthenticationService;
import com.bondhub.authservice.util.CookieUtil;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.HttpRequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthController {

    AuthenticationService authenticationService;
    CookieUtil cookieUtil;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        log.info("POST /auth/login - Login for email: {}, deviceId: {}", request.email(), request.deviceId());

        String userAgent = HttpRequestUtil.getUserAgent(httpRequest);
        String ipAddress = HttpRequestUtil.getClientIpAddress(httpRequest);

        TokenResponse tokenResponse = authenticationService.login(request, userAgent, ipAddress);

        // Set refresh token in HttpOnly cookie
        ResponseCookie cookie = cookieUtil.createRefreshTokenCookie(
                tokenResponse.refreshToken(), tokenResponse.refreshTokenExpirationMs());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }

    /**
     * OLD SINGLE-STEP REGISTRATION (deprecated, use two-step flow instead)
     */
    @Deprecated
    @PostMapping("/register/old")
    public ResponseEntity<ApiResponse<TokenResponse>> registerOld(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /auth/register/old - Registration for email: {}", request.email());
        TokenResponse tokenResponse = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tokenResponse));
    }

    /**
     * TWO-STEP REGISTRATION - Step 1: Initiate registration
     * Validates email, generates OTP, sends email
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterInitResponse>> initiateRegistration(
            @Valid @RequestBody RegisterInitRequest request) {

        log.info("🔵 POST /auth/register - START - Email: {}", request.email());

        try {
            RegisterInitResponse response = authenticationService.initiateRegistration(request);

            ApiResponse<RegisterInitResponse> apiResponse = ApiResponse.success(response);

            ResponseEntity<ApiResponse<RegisterInitResponse>> responseEntity = ResponseEntity.ok(apiResponse);
            log.info(" ResponseEntity created with status: {}", responseEntity.getStatusCode());

            return responseEntity;
        } catch (Exception e) {
            log.error("ERROR in controller: ", e);
            throw e;
        }
    }

    /**
     * TWO-STEP REGISTRATION - Step 2: Verify OTP and complete registration
     * Creates account and auto-login
     */
    @PostMapping("/register/verify")
    public ResponseEntity<ApiResponse<TokenResponse>> verifyAndCompleteRegistration(
            @Valid @RequestBody RegisterVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        log.info("POST /auth/register/verify - Verifying OTP for email: {}", request.email());

        String userAgent = HttpRequestUtil.getUserAgent(httpRequest);
        String ipAddress = HttpRequestUtil.getClientIpAddress(httpRequest);

        TokenResponse tokenResponse = authenticationService.verifyAndCompleteRegistration(
                request, userAgent, ipAddress);

        // Set refresh token in HttpOnly cookie
        ResponseCookie cookie = cookieUtil.createRefreshTokenCookie(
                tokenResponse.refreshToken(), tokenResponse.refreshTokenExpirationMs());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tokenResponse));
    }

    /**
     * FORGOT PASSWORD - Step 1: Request password reset
     * Generates OTP and sends email
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        log.info("POST /auth/forgot-password - for email: {}", request.email());

        ForgotPasswordResponse response = authenticationService.forgotPassword(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * RESET PASSWORD - Step 2: Verify OTP and set new password
     * Updates password and auto-logins
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<TokenResponse>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        log.info("POST /auth/reset-password - for email: {}", request.email());

        String userAgent = HttpRequestUtil.getUserAgent(httpRequest);
        String ipAddress = HttpRequestUtil.getClientIpAddress(httpRequest);

        TokenResponse tokenResponse = authenticationService.resetPassword(request, userAgent, ipAddress);

        if (Boolean.TRUE.equals(request.logoutOtherDevices())) {
            authenticationService.logoutAllOtherDevices(tokenResponse.refreshToken());
        }

        // Set refresh token in HttpOnly cookie
        ResponseCookie cookie = cookieUtil.createRefreshTokenCookie(
                tokenResponse.refreshToken(), tokenResponse.refreshTokenExpirationMs());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }

    /**
     * CHANGE PASSWORD - For authenticated users
     * Updates password without requiring OTP
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @CookieValue(value = CookieUtil.REFRESH_TOKEN_COOKIE_NAME, required = false) String cookieRefreshToken) {

        log.info("POST /auth/change-password - Password change request");

        authenticationService.changePassword(request);

        if (Boolean.TRUE.equals(request.logoutOtherDevices())) {
            authenticationService.logoutAllOtherDevices(cookieRefreshToken);
        }

        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshRequest request,
            @CookieValue(value = CookieUtil.REFRESH_TOKEN_COOKIE_NAME, required = false) String cookieRefreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        log.info("POST /auth/refresh - Refresh for device: {}", request.deviceId());

        String refreshToken = cookieRefreshToken;
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = request.refreshToken();
            log.info("Refresh token not found in cookie, checking request body...");
        }

        if (refreshToken == null || refreshToken.isBlank()) {
            log.error("Refresh token is missing from both cookie and request body");
        }

        String userAgent = HttpRequestUtil.getUserAgent(httpRequest);
        String ipAddress = HttpRequestUtil.getClientIpAddress(httpRequest);

        TokenResponse tokenResponse = authenticationService.refresh(refreshToken, request, userAgent, ipAddress);

        // Update refresh token in cookie (Rotation)
        ResponseCookie cookie = cookieUtil.createRefreshTokenCookie(
                tokenResponse.refreshToken(), tokenResponse.refreshTokenExpirationMs());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) LogoutRequest request,
            @CookieValue(value = CookieUtil.REFRESH_TOKEN_COOKIE_NAME, required = false) String cookieRefreshToken,
            HttpServletResponse httpResponse) {

        log.info("POST /auth/logout - Logout request");

        String refreshToken = (request != null && request.refreshToken() != null)
                ? request.refreshToken()
                : cookieRefreshToken;

        authenticationService.logout(refreshToken);

        // Clear cookie
        ResponseCookie cookie = cookieUtil.clearRefreshTokenCookie();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/logout-others")
    public ResponseEntity<ApiResponse<Void>> logoutOtherDevices(
            @RequestBody(required = false) LogoutRequest request,
            @CookieValue(value = CookieUtil.REFRESH_TOKEN_COOKIE_NAME, required = false) String cookieRefreshToken) {

        log.info("POST /auth/logout-others - Logout all other devices request");

        String refreshToken = (request != null && request.refreshToken() != null)
                ? request.refreshToken()
                : cookieRefreshToken;

        authenticationService.logoutAllOtherDevices(refreshToken);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/logout-device")
    public ResponseEntity<ApiResponse<Void>> logoutDevice(
            @Valid @RequestBody LogoutDeviceRequest request,
            @CookieValue(value = CookieUtil.REFRESH_TOKEN_COOKIE_NAME, required = false) String cookieRefreshToken) {

        log.info("POST /auth/logout-device - Logout specific device request");

        String refreshToken = (request != null && request.refreshToken() != null)
                ? request.refreshToken()
                : cookieRefreshToken;

        authenticationService.logoutDevice(request.sessionId(), refreshToken);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(@RequestParam String token) {
        boolean isValid = authenticationService.validateToken(token);
        return ResponseEntity.ok(ApiResponse.success(isValid));
    }
}
