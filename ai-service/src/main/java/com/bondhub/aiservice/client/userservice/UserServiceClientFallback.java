package com.bondhub.aiservice.client.userservice;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.request.BioUpdateRequest;
import com.bondhub.common.dto.client.userservice.user.request.UserUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public ApiResponse<Map<String, Object>> getMyProfile() {
        log.error("[Fallback] UserService unavailable — getMyProfile");
        return new ApiResponse<>(503, "Hệ thống thông tin người dùng đang bảo trì. Vui lòng thử lại sau.", null, null);
    }

    @Override
    public ApiResponse<Map<String, Object>> updateMyProfile(UserUpdateRequest request) {
        log.error("[Fallback] UserService unavailable — updateMyProfile");
        return new ApiResponse<>(503, "Không thể cập nhật hồ sơ lúc này. Vui lòng thử lại sau.", null, null);
    }

    @Override
    public ApiResponse<Map<String, Object>> updateMyBio(BioUpdateRequest request) {
        log.error("[Fallback] UserService unavailable — updateMyBio");
        return new ApiResponse<>(503, "Không thể cập nhật bio lúc này. Vui lòng thử lại sau.", null, null);
    }
}
