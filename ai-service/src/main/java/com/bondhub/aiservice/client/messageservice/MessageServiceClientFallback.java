package com.bondhub.aiservice.client.messageservice;

import com.bondhub.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class MessageServiceClientFallback implements MessageServiceClient {

    @Override
    public ApiResponse<Map<String, Object>> getMyConversations(int page, int size) {
        log.error("[Fallback] MessageService unavailable — getMyConversations");
        return new ApiResponse<>(503, "Hệ thống tin nhắn đang bảo trì. Vui lòng thử lại sau.", null, null);
    }

    @Override
    public ApiResponse<Map<String, Object>> getMessages(String conversationId, int page, int size) {
        log.error("[Fallback] MessageService unavailable — getMessages({})", conversationId);
        return new ApiResponse<>(503, "Không thể tải tin nhắn của phòng chat này lúc này.", null, null);
    }
}
