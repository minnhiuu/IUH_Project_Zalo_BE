package com.bondhub.socialfeedservice.client;

import com.bondhub.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Set;

@FeignClient(name = "friend-service")
public interface FriendServiceClient {

    @GetMapping("/internal/friends/user/{userId}/friend-ids")
    ApiResponse<Set<String>> getFriendIdsInternal(@PathVariable("userId") String userId);
}
