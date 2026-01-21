package com.bondhub.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
public class JwtProperties {

    private String secret;

    private Access access = new Access();
    private Refresh refresh = new Refresh();

    @Getter
    @Setter
    public static class Access {
        @Min(value = 1, message = "Access token expiration must be greater than 0")
        private Long expiration = 3600000L;
    }

    @Getter
    @Setter
    public static class Refresh {
        @Min(value = 1, message = "Refresh token expiration must be greater than 0")
        private Long expirationWeb = 1209600000L;

        @Min(value = 1, message = "Refresh token expiration must be greater than 0")
        private Long expirationMobile = 31536000000L;
    }

    public Long getAccessTokenExpiration() {
        return access.getExpiration();
    }

    public Long getRefreshExpirationWeb() {
        return refresh.getExpirationWeb();
    }

    public Long getRefreshExpirationMobile() {
        return refresh.getExpirationMobile();
    }
}
