package com.bondhub.socketservice.config;

import com.bondhub.common.security.UserPrincipal;
import com.bondhub.common.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * WebSocket configuration for socket-service.
 * Validates JWT on STOMP CONNECT frame and sets Principal so that
 * convertAndSendToUser() routing works correctly.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            if (jwtUtil.validateToken(token)) {
                                String accountId = jwtUtil.extractAccountId(token);
                                String userId   = jwtUtil.extractUserId(token);
                                String email    = jwtUtil.extractEmail(token);
                                String role     = jwtUtil.extractRole(token);
                                String jti      = jwtUtil.extractJti(token);
                                long remaining  = jwtUtil.getRemainingTtl(token);

                                List<SimpleGrantedAuthority> authorities = role != null
                                        ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                                        : List.of();

                                UserPrincipal userPrincipal = new UserPrincipal(accountId, userId, email, jti, remaining, authorities);

                                // Use userId as the STOMP routing key for convertAndSendToUser()
                                UsernamePasswordAuthenticationToken auth =
                                        new UsernamePasswordAuthenticationToken(userPrincipal, null, authorities) {
                                            @Override
                                            public String getName() {
                                                return userPrincipal.getUserId();
                                            }
                                        };

                                accessor.setUser(auth);
                                log.info("[WS] Authenticated user: {}", userId);
                            } else {
                                log.warn("[WS] Invalid JWT token – connection refused");
                                return null; // reject the CONNECT frame
                            }
                        } catch (Exception e) {
                            log.error("[WS] Authentication error: {}", e.getMessage());
                            return null; // reject
                        }
                    }
                } else if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }

                return message;
            }
        });
    }
}
