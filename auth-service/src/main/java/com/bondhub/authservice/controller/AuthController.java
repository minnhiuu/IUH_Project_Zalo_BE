package com.bondhub.authservice.controller;

import com.bondhub.authservice.dto.auth.request.LoginRequest;
import com.bondhub.authservice.dto.auth.request.LogoutRequest;
import com.bondhub.authservice.dto.auth.request.RefreshRequest;
import com.bondhub.authservice.dto.auth.request.RegisterRequest;
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

        log.info("POST /auth/login - Login for phone: {}, deviceId: {}", request.phoneNumber(), request.deviceId());

        String userAgent = HttpRequestUtil.getUserAgent(httpRequest);
        String ipAddress = HttpRequestUtil.getClientIpAddress(httpRequest);

        TokenResponse tokenResponse = authenticationService.login(request, userAgent, ipAddress);

        // Set refresh token in HttpOnly cookie
        ResponseCookie cookie = cookieUtil.createRefreshTokenCookie(
                tokenResponse.refreshToken(),
                tokenResponse.expiresIn());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /auth/register - Registration for email: {}", request.email());
        TokenResponse tokenResponse = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tokenResponse));
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
                tokenResponse.refreshToken(),
                tokenResponse.expiresIn());
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

    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(@RequestParam String token) {
        boolean isValid = authenticationService.validateToken(token);
        return ResponseEntity.ok(ApiResponse.success(isValid));
    }
}
