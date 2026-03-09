package com.bondhub.userservice.service.admin;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.userservice.dto.response.UserActivityLogResponse;
import com.bondhub.userservice.dto.response.UserAdminDetailResponse;
import com.bondhub.userservice.dto.response.UserAdminResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminUserService {

    PageResponse<List<UserAdminResponse>> getAllUsers(String name, String phone, String email, String status, Pageable pageable);

    /**
     * Get full user detail with audit info for admin
     */
    UserAdminDetailResponse getUserDetail(String userId);

    /**
     * Get activity log timeline for a specific user
     */
    PageResponse<List<UserActivityLogResponse>> getUserActivityLogs(String userId, Pageable pageable);

    /**
     * Ban a user account
     */
    void banUser(String userId, String reason);

    /**
     * Unban a user account
     */
    void unbanUser(String userId);
}
