package com.bondhub.common.config;

import com.bondhub.common.security.SecurityContextFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Common Security Configuration
 * Provides security context setup for servlet-based services behind API Gateway
 * Enable this by setting: bondhub.security.gateway-auth.enabled=true
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bondhub.security.gateway-auth.enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonSecurityConfig {

    private final SecurityContextFilter securityContextFilter;
    private final SecurityProperties securityProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // Permit base endpoints
                    auth.requestMatchers("/").permitAll();
                    auth.requestMatchers("/error", "/actuator/**").permitAll();

                    // Permit configured public endpoints
                    if (securityProperties.getPublicEndpoints() != null
                            && !securityProperties.getPublicEndpoints().isEmpty()) {
                        auth.requestMatchers(securityProperties.getPublicEndpoints().toArray(String[]::new))
                                .permitAll();
                    }

                    // All other requests require authentication
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            // Let @RestControllerAdvice handle exceptions, don't return 403
                        }))
                .addFilterBefore(securityContextFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
