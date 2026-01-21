package com.bondhub.authservice.service.auth;

import com.bondhub.common.utils.JwtUtil;
import com.bondhub.authservice.service.token.TokenStoreService;
import com.bondhub.authservice.util.SecurityUtil;
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

    @Mock
    private TokenStoreService tokenStoreService;

    @Mock
    private SecurityUtil securityUtil;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    @Test
    public void testLogout_ValidToken() {
        // Arrange
        String refreshToken = "valid.refresh.token";
        String sessionId = "session-123";

        when(jwtUtil.validateToken(refreshToken)).thenReturn(true);
        when(jwtUtil.extractSessionId(refreshToken)).thenReturn(sessionId);
        when(securityUtil.isAuthenticated()).thenReturn(false); // Simulating no auth context for simplicity

        // Act
        authenticationService.logout(refreshToken);

        // Assert
        verify(jwtUtil).validateToken(refreshToken);
        verify(jwtUtil).extractSessionId(refreshToken);
        verify(tokenStoreService).revokeRefreshSession(sessionId);
    }

    @Test
    public void testLogout_InvalidToken() {
        // Arrange
        String refreshToken = "invalid.refresh.token";

        when(jwtUtil.validateToken(refreshToken)).thenReturn(false);
        when(securityUtil.isAuthenticated()).thenReturn(false);

        // Act
        authenticationService.logout(refreshToken);

        // Assert
        verify(jwtUtil).validateToken(refreshToken);
        verify(tokenStoreService, never()).revokeRefreshSession(anyString());
    }
}
