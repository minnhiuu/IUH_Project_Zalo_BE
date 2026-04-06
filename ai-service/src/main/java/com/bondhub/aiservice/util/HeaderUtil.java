package com.bondhub.aiservice.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}
