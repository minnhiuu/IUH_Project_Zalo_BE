package com.bondhub.authservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qr")
@Data
public class QrProperties {
    private long expirationSeconds;
    private String contentPrefix;
    private long waitTimeoutMs;
}
