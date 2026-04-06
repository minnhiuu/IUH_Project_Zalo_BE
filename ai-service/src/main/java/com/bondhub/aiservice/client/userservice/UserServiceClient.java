package com.bondhub.aiservice.client.userservice;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.request.BioUpdateRequest;
import com.bondhub.common.dto.client.userservice.user.request.UserUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "user-service", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    /** Lấy thông tin profile của currentUser */
    @GetMapping("/users/me")
    ApiResponse<Map<String, Object>> getMyProfile();

    /** Cập nhật toàn bộ profile (fullName, dob, bio, gender) */
    @PutMapping("/users/me")
    ApiResponse<Map<String, Object>> updateMyProfile(@RequestBody UserUpdateRequest request);

    /** Cập nhật bio của currentUser */
    @PutMapping("/users/profile/bio")
    ApiResponse<Map<String, Object>> updateMyBio(@RequestBody BioUpdateRequest request);
}
