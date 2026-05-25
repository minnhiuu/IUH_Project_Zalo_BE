package com.bondhub.searchservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.friendservice.UserSearchContextRequest;
import com.bondhub.common.dto.client.friendservice.UserSearchContextResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "friend-service")
public interface FriendServiceClient {

    @PostMapping("/internal/friends/search-context")
    ApiResponse<List<UserSearchContextResponse>> getUserSearchContext(@RequestBody UserSearchContextRequest request);
}
