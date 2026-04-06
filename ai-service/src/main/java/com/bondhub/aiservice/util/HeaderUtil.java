package com.bondhub.aiservice.util;

import com.bondhub.aiservice.security.AiSecurityContextHolder;
import com.bondhub.aiservice.security.AiUserContextInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class HeaderUtil {

    /** Các header cần forward sang downstream services — đúng case */
    private static final List<String> FORWARD_HEADER_NAMES = List.of(
            "Authorization",
            "X-User-Id",
            "X-Account-Id",
            "X-User-Email",
            "X-User-Roles",
            "X-JWT-Id",
            "X-Remaining-TTL"
    );

    /**
     * WebFlux HttpHeaders store keys case-insensitively.
     * We re-map to exact casing that downstream servlet SecurityContextFilter expects.
     */
    public static Map<String, String> extractForwardHeaders(ServerHttpRequest request) {
        Map<String, String> result = new HashMap<>();
        for (String headerName : FORWARD_HEADER_NAMES) {
            String value = request.getHeaders().getFirst(headerName);
            if (value != null) {
                result.put(headerName, value);
            }
        }
        return result;
    }

    public static void activateHeaders(ConcurrentHashMap<String, String> convToUser) {
        Map<String, String> headers = null;
        for (Map.Entry<String, String> entry : convToUser.entrySet()) {
            headers = AiSecurityContextHolder.getByUserId(entry.getValue());
            if (headers != null) break;
        }
        if (headers != null) {
            AiUserContextInterceptor.FORWARD_HEADERS.set(headers);
            log.info("[Tool] Headers activated: {}", headers.keySet());
        } else {
            log.warn("[Tool] No headers found from any registered conversation!");
        }
    }

    public static void deactivateHeaders() {
        AiUserContextInterceptor.FORWARD_HEADERS.remove();
    }
}
