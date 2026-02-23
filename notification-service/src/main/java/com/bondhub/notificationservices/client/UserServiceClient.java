package com.bondhub.notificationservices.client;

import com.bondhub.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${services.user-service.url}")
public interface UserServiceClient {

    @GetMapping("/internal/users/exists/{userId}")
    ResponseEntity<ApiResponse<Boolean>> existsById(@PathVariable String userId);
}
