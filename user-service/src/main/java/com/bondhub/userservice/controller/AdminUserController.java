package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.userservice.dto.request.BanUserRequest;
import com.bondhub.userservice.dto.response.UserActivityLogResponse;
import com.bondhub.userservice.dto.response.UserAdminDetailResponse;
import com.bondhub.userservice.dto.response.UserAdminResponse;
import com.bondhub.userservice.service.admin.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<List<UserAdminResponse>>>> getAllUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getAllUsers(name, phone, email, status, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserAdminDetailResponse>> getUserDetail(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUserDetail(id)));
    }

    @GetMapping("/{id}/activity-logs")
    public ResponseEntity<ApiResponse<PageResponse<List<UserActivityLogResponse>>>> getUserActivityLogs(
            @PathVariable String id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUserActivityLogs(id, pageable)));
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<ApiResponse<Void>> banUser(
            @PathVariable String id,
            @Valid @RequestBody BanUserRequest request) {
        adminUserService.banUser(id, request.reason());
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<ApiResponse<Void>> unbanUser(@PathVariable String id) {
        adminUserService.unbanUser(id);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
