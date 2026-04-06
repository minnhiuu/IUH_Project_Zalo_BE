package com.bondhub.aiservice.client.friendservice;

import com.bondhub.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "friend-service", fallback = FriendServiceClientFallback.class)
public interface FriendServiceClient {

    /** Lấy danh sách bạn bè của currentUser (phân trang) */
    @GetMapping("/friendships/friends")
    ApiResponse<Map<String, Object>> getMyFriends(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size);

    /** Đếm số bạn bè chung với userId */
    @GetMapping("/friendships/mutual/{userId}/count")
    ApiResponse<Integer> countMutualFriends(@PathVariable("userId") String userId);
}
