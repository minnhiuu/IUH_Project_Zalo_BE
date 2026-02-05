package com.bondhub.authservice.controller;

import com.bondhub.authservice.dto.device.request.DeviceCreateRequest;
import com.bondhub.authservice.dto.device.request.DeviceUpdateRequest;
import com.bondhub.authservice.dto.device.response.DeviceResponse;
import com.bondhub.authservice.service.device.DeviceService;
import com.bondhub.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth/devices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DeviceController {

    DeviceService deviceService;

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> createDevice(@Valid @RequestBody DeviceCreateRequest request) {
        log.info("REST request to create device with sessionId: {}", request.sessionId());
        DeviceResponse deviceResponse = deviceService.createDevice(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(deviceResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> getDeviceById(@PathVariable String id) {
        log.info("REST request to get device by id: {}", id);
        DeviceResponse deviceResponse = deviceService.getDeviceById(id);
        return ResponseEntity.ok(ApiResponse.success(deviceResponse));
    }

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

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> updateDevice(
            @PathVariable String id,
            @Valid @RequestBody DeviceUpdateRequest request) {
        log.info("REST request to update device with id: {}", id);
        DeviceResponse deviceResponse = deviceService.updateDevice(id, request);
        return ResponseEntity.ok(ApiResponse.success(deviceResponse));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(@PathVariable String id) {
        log.info("REST request to delete device with id: {}", id);
        deviceService.deleteDevice(id);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @DeleteMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<Void>> deleteDevicesByAccountId(@PathVariable String accountId) {
        log.info("REST request to delete all devices for accountId: {}", accountId);
        deviceService.deleteDevicesByAccountId(accountId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @GetMapping("/exists/session/{sessionId}")
    public ResponseEntity<ApiResponse<Boolean>> existsBySessionId(@PathVariable String sessionId) {
        log.info("REST request to check if device exists by sessionId: {}", sessionId);
        boolean exists = deviceService.existsBySessionId(sessionId);
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
}
