package com.bondhub.authservice.service.auth;

import com.bondhub.authservice.dto.auth.request.LogoutRequest;
import com.bondhub.common.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceImplTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    @Test
    public void testLogout_ValidToken() {
        // Arrange
        String refreshToken = "valid.refresh.token";
        LogoutRequest request = new LogoutRequest(refreshToken);

        when(jwtUtil.validateToken(refreshToken)).thenReturn(true);
        when(jwtUtil.extractUserId(refreshToken)).thenReturn("user-123");

        // Act
        authenticationService.logout(request);

        // Assert
        verify(jwtUtil).validateToken(refreshToken);
        verify(jwtUtil).extractUserId(refreshToken);
    }

    @Test
    public void testLogout_InvalidToken() {
        // Arrange
        String refreshToken = "invalid.refresh.token";
        LogoutRequest request = new LogoutRequest(refreshToken);

        when(jwtUtil.validateToken(refreshToken)).thenReturn(false);

        // Act
        authenticationService.logout(request);

        // Assert
        verify(jwtUtil).validateToken(refreshToken);
        verify(jwtUtil, never()).extractUserId(anyString());
    }
}
