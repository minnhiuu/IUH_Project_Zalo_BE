package com.bondhub.authservice.controller;

import com.bondhub.authservice.dto.device.request.DeviceCreateRequest;
import com.bondhub.authservice.dto.device.request.DeviceUpdateRequest;
import com.bondhub.authservice.dto.device.response.DeviceListResponse;
import com.bondhub.authservice.dto.device.response.DeviceResponse;
import com.bondhub.authservice.service.device.DeviceService;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.JwtUtil;
import com.bondhub.common.utils.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth/devices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DeviceController {

    DeviceService deviceService;
    SecurityUtil securityUtil;
    JwtUtil jwtUtil;

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<DeviceResponse>> getDeviceBySessionId(@PathVariable String sessionId) {
        log.info("REST request to get device by sessionId: {}", sessionId);
        DeviceResponse deviceResponse = deviceService.getDeviceBySessionId(sessionId);
        return ResponseEntity.ok(ApiResponse.success(deviceResponse));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getDevicesByAccountId(@PathVariable String accountId) {
        log.info("REST request to get devices by accountId: {}", accountId);
        List<DeviceResponse> deviceResponses = deviceService.getDevicesByAccountId(accountId);
        return ResponseEntity.ok(ApiResponse.success(deviceResponses));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getAllDevices() {
        log.info("REST request to get all devices");
        List<DeviceResponse> deviceResponses = deviceService.getAllDevices();
        return ResponseEntity.ok(ApiResponse.success(deviceResponses));
    }

    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceListResponse>> getGroupedActiveDevicesWithSessions(
            HttpServletRequest request) {
        log.info("REST request to get grouped active devices with sessions");

        // Get the account ID from the security context
        String accountId = securityUtil.getCurrentAccountId();

        // Extract session ID from the JWT token if available
        String currentSessionId = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                currentSessionId = jwtUtil.extractSessionId(token);
            } catch (Exception e) {
                log.warn("Failed to extract sessionId from token: {}", e.getMessage());
            }
        }

        DeviceListResponse groupedResponse = deviceService.getGroupedActiveDevicesWithSessions(accountId,
                currentSessionId);

        return ResponseEntity.ok(ApiResponse.success(groupedResponse));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteMyDevice(@PathVariable String id) {
        log.info("REST request to delete device with id: {}", id);
        String accountId = securityUtil.getCurrentAccountId();
        deviceService.deleteDeviceById(id, accountId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
