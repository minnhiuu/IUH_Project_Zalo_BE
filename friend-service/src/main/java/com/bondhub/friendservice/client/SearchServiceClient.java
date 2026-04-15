package com.bondhub.friendservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "search-service", path = "/search")
public interface SearchServiceClient {

    @PostMapping("/users/by-phones")
    ApiResponse<List<UserSummaryResponse>> findUsersByPhones(@RequestBody List<String> phones);
}
