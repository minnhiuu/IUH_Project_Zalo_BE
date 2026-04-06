package com.bondhub.aiservice.client.friendservice;

import com.bondhub.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class FriendServiceClientFallback implements FriendServiceClient {

    @Override
    public ApiResponse<Map<String, Object>> getMyFriends(int page, int size) {
        log.error("[Fallback] FriendService unavailable — getMyFriends");
        return new ApiResponse<>(503, "Hệ thống danh sách bạn bè hiện đang bảo trì. Vui lòng thử lại sau.", null, null);
    }

    @Override
    public ApiResponse<Integer> countMutualFriends(String userId) {
        log.error("[Fallback] FriendService unavailable — countMutualFriends({})", userId);
        return new ApiResponse<>(503, "Không thể truy xuất số bạn chung lúc này.", null, null);
    }
}
