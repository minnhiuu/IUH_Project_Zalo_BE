package com.bondhub.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "bondhub.security")
public class SecurityProperties {

    private GatewayAuth gatewayAuth = new GatewayAuth();
    private List<String> publicEndpoints = new ArrayList<>();

    @Data
    public static class GatewayAuth {
        private boolean enabled = false;
    }
}
