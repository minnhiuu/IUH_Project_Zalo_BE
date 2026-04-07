package com.bondhub.aiservice.security;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lưu trữ ngữ cảnh bảo mật (Headers) theo Conversation ID.
 * Cung cấp 2 cách lookup:
 *   1. get(convId) — chính xác theo conversation
 *   2. getByUserId(userId) — cho Tools (không có convId)
 */
@Slf4j
public class AiSecurityContextHolder {

    // Primary: convId → headers
    private static final Map<String, Map<String, String>> CONTEXT_STORE = new ConcurrentHashMap<>();

    // Reverse index: userId → convId (mỗi user chỉ có 1 active request tại 1 thời điểm)
    private static final Map<String, String> USER_TO_CONV = new ConcurrentHashMap<>();

    public static void bind(String convId, Map<String, String> headers) {
        CONTEXT_STORE.put(convId, headers);
        // Index thêm theo userId để Tools có thể tìm mà không cần convId
        String userId = headers.get("X-User-Id");
        if (userId != null) {
            USER_TO_CONV.put(userId, convId);
        }
        log.debug("[SecurityContext] Bound headers for convId={}, userId={}", convId, userId);
    }

    public static Map<String, String> get(String convId) {
        return CONTEXT_STORE.get(convId);
    }

    /** Lookup headers bằng userId — dùng cho Tools vì @MemoryId không hoạt động trên @Tool */
    public static Map<String, String> getByUserId(String userId) {
        String convId = USER_TO_CONV.get(userId);
        if (convId == null) {
            log.warn("[SecurityContext] No convId found for userId={}", userId);
            return null;
        }
        return CONTEXT_STORE.get(convId);
    }

    public static void unbind(String convId) {
        Map<String, String> removed = CONTEXT_STORE.remove(convId);
        if (removed != null) {
            String userId = removed.get("X-User-Id");
            if (userId != null) {
                USER_TO_CONV.remove(userId);
            }
        }
        log.debug("[SecurityContext] Unbound convId={}", convId);
    }
}
