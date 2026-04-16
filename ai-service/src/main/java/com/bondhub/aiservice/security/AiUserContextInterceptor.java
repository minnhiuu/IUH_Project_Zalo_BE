package com.bondhub.aiservice.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Feign RequestInterceptor chuyên dùng cho ai-service.
 * Đọc headers từ ThreadLocal (được set bởi Tool call trước khi Feign request).
 * Hoạt động trơn tru trong WebFlux (không đụng tới Servlet API).
 */
@Component
@Slf4j
public class AiUserContextInterceptor implements RequestInterceptor {

    // ThreadLocal này chỉ tồn tại trong vòng đời ngắn của 1 Tool Call
    public static final ThreadLocal<Map<String, String>> FORWARD_HEADERS = new ThreadLocal<>();

    @Override
    public void apply(RequestTemplate template) {
        Map<String, String> headers = FORWARD_HEADERS.get();
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(template::header);
            log.info("[Feign] Interceptor forwarding headers: {}", headers.keySet());
        } else {
            log.warn("[Feign] Interceptor: NO headers in ThreadLocal!");
        }
    }
}
