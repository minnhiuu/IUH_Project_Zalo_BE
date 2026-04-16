package com.bondhub.aiservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Feign configuration riêng cho ai-service (WebFlux).
 * - Cung cấp HttpMessageConverters (WebFlux không auto-config cái này)
 * - Custom ErrorDecoder trả message thân thiện thay vì throw AppException
 */
@Configuration
@Slf4j
public class AiFeignConfig {

    /**
     * WebFlux không tạo HttpMessageConverters bean.
     * Feign's SpringDecoder cần nó để deserialize JSON response → ApiResponse.
     */
    @Bean
    public HttpMessageConverters httpMessageConverters(ObjectMapper objectMapper) {
        return new HttpMessageConverters(
                new MappingJackson2HttpMessageConverter(objectMapper)
        );
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            String body = "";
            try {
                if (response.body() != null) {
                    body = new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {}

            log.error("[AiFeign] Error calling {} | status={} | body={}", methodKey, response.status(), body);

            String friendlyMsg = switch (response.status()) {
                case 401, 403 -> "Không có quyền truy cập. Vui lòng đăng nhập lại.";
                case 404 -> "Không tìm thấy tài nguyên yêu cầu.";
                case 503 -> "Hệ thống đang bảo trì. Vui lòng thử lại sau.";
                default -> "Hệ thống gặp lỗi (HTTP " + response.status() + "). Vui lòng thử lại sau.";
            };
            return new RuntimeException(friendlyMsg);
        };
    }
}
