package com.bondhub.authservice.service.device;

import com.bondhub.authservice.dto.device.request.DeviceCreateRequest;
import com.bondhub.authservice.dto.device.request.DeviceUpdateRequest;
import com.bondhub.authservice.dto.device.response.DeviceResponse;
import com.bondhub.authservice.mapper.DeviceMapper;
import com.bondhub.authservice.model.Device;
import com.bondhub.authservice.model.redis.RefreshTokenSession;
import com.bondhub.authservice.repository.DeviceRepository;
import com.bondhub.authservice.repository.redis.RefreshTokenSessionRepository;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DeviceServiceImpl implements DeviceService {

    DeviceRepository deviceRepository;
    DeviceMapper deviceMapper;
    RefreshTokenSessionRepository refreshTokenSessionRepository;

    @Override
    public DeviceResponse createDevice(DeviceCreateRequest request) {
        log.info("Creating new device with sessionId: {}", request.sessionId());

        if (request.sessionId() != null && deviceRepository.existsBySessionId(request.sessionId())) {
            log.warn("Device with sessionId {} already exists", request.sessionId());
            throw new AppException(ErrorCode.DEV_SESSION_ID_ALREADY_USED);
        }

        Device device = deviceMapper.toEntity(request);
        Device savedDevice = deviceRepository.save(device);
        log.info("Device created successfully with id: {}", savedDevice.getId());
        return deviceMapper.toResponse(savedDevice, isSessionActive(savedDevice.getSessionId()));
    }

    @Override
    public DeviceResponse getDeviceById(String id) {
        log.info("Fetching device with id: {}", id);
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Device not found with id: {}", id);
                    return new AppException(ErrorCode.DEV_DEVICE_NOT_FOUND);
                });

        log.info("Device found with id: {}", id);
        return deviceMapper.toResponse(device, isSessionActive(device.getSessionId()));
    }

    @Override
    public DeviceResponse getDeviceBySessionId(String sessionId) {
        log.info("Fetching device with sessionId: {}", sessionId);
        Device device = deviceRepository.findBySessionId(sessionId)
                .orElseThrow(() -> {
                    log.warn("Device not found with sessionId: {}", sessionId);
                    return new AppException(ErrorCode.DEV_DEVICE_NOT_FOUND);
                });

        log.info("Device found with sessionId: {}", sessionId);
        return deviceMapper.toResponse(device, isSessionActive(device.getSessionId()));
    }

    @Override
    public List<DeviceResponse> getDevicesByAccountId(String accountId) {
        log.info("Fetching devices for accountId: {}", accountId);
        List<Device> devices = deviceRepository.findByAccountId(accountId);
        log.info("Found {} devices for accountId: {}", devices.size(), accountId);
        return devices.stream()
                .map(device -> deviceMapper.toResponse(device, isSessionActive(device.getSessionId())))
                .toList();
    }

    @Override
    public List<DeviceResponse> getAllDevices() {
        log.info("Fetching all devices");
        List<Device> devices = deviceRepository.findAll();
        log.info("Found {} devices", devices.size());
        return devices.stream()
                .map(device -> deviceMapper.toResponse(device, isSessionActive(device.getSessionId())))
                .toList();
    }

    @Override
    public DeviceResponse updateDevice(String id, DeviceUpdateRequest request) {
        log.info("Updating device with id: {}", id);
        Device deviceToUpdate = deviceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Device not found with id: {}", id);
                    return new AppException(ErrorCode.DEV_DEVICE_NOT_FOUND);
                });

        if (request.sessionId() != null && !request.sessionId().equals(deviceToUpdate.getSessionId())) {
            if (deviceRepository.existsBySessionId(request.sessionId())) {
                log.warn("SessionId {} already exists for another device", request.sessionId());
                throw new AppException(ErrorCode.DEV_SESSION_ID_ALREADY_USED);
            }
        }

        deviceMapper.updateEntityFromRequest(deviceToUpdate, request);
        Device updatedDevice = deviceRepository.save(deviceToUpdate);
        log.info("Device updated successfully with id: {}", id);
        return deviceMapper.toResponse(updatedDevice, isSessionActive(updatedDevice.getSessionId()));
    }

    @Override
    public DeviceResponse updateDeviceBySessionId(String sessionId, DeviceUpdateRequest request) {
        log.info("Updating device with sessionId: {}", sessionId);
        Device deviceToUpdate = deviceRepository.findBySessionId(sessionId)
                .orElseThrow(() -> {
                    log.warn("Device not found with sessionId: {}", sessionId);
                    return new AppException(ErrorCode.DEV_DEVICE_NOT_FOUND);
                });

        deviceMapper.updateEntityFromRequest(deviceToUpdate, request);
        Device updatedDevice = deviceRepository.save(deviceToUpdate);
        log.info("Device updated successfully with sessionId: {}", sessionId);
        return deviceMapper.toResponse(updatedDevice, isSessionActive(updatedDevice.getSessionId()));
    }

    @Override
    public void deleteDevice(String id) {
        log.info("Deleting device with id: {}", id);

        if (!deviceRepository.existsById(id)) {
            log.warn("Device not found with id: {}", id);
            throw new AppException(ErrorCode.DEV_DEVICE_NOT_FOUND);
        }

        deviceRepository.deleteById(id);
        log.info("Device deleted successfully with id: {}", id);
    }

    @Override
    public void deleteDevicesByAccountId(String accountId) {
        log.info("Deleting all devices for accountId: {}", accountId);
        deviceRepository.deleteByAccountId(accountId);
        log.info("All devices deleted for accountId: {}", accountId);
    }

    @Override
    public boolean existsBySessionId(String sessionId) {
        log.info("Checking if device exists with sessionId: {}", sessionId);
        boolean exists = deviceRepository.existsBySessionId(sessionId);
        log.info("Device with sessionId {} exists: {}", sessionId, exists);
        return exists;
    }

    @Override
    public DeviceResponse saveOrUpdateDevice(DeviceCreateRequest request) {
        // Try to find an existing device for this physical device + account combination
        if (request.deviceId() != null && !request.deviceId().isBlank()
                && request.accountId() != null && !request.accountId().isBlank()) {

            Optional<Device> existing = deviceRepository.findByDeviceIdAndAccountId(
                    request.deviceId(), request.accountId());

            if (existing.isPresent()) {
                Device deviceToUpdate = existing.get();
                log.info("Updating existing device {} for accountId: {}", deviceToUpdate.getId(), request.accountId());

                // Update only the session-related fields
                DeviceUpdateRequest updateRequest = DeviceUpdateRequest.builder()
                        .sessionId(request.sessionId())
                        .ipAddress(request.ipAddress())
                        .lastActiveTime(request.lastActiveTime())
                        .deviceName(request.deviceName())
                        .browser(request.browser())
                        .os(request.os())
                        .deviceType(request.deviceType())
                        .build();

                deviceMapper.updateEntityFromRequest(deviceToUpdate, updateRequest);
                Device updatedDevice = deviceRepository.save(deviceToUpdate);
                log.info("Device updated (upsert) with sessionId: {}", request.sessionId());
                return deviceMapper.toResponse(updatedDevice, isSessionActive(updatedDevice.getSessionId()));
            }
        }

        // No existing device found → create a new one
        log.info("Creating new device (upsert) with sessionId: {}", request.sessionId());
        Device device = deviceMapper.toEntity(request);
        Device savedDevice = deviceRepository.save(device);
        log.info("Device created (upsert) with id: {}", savedDevice.getId());
        return deviceMapper.toResponse(savedDevice, isSessionActive(savedDevice.getSessionId()));
    }

    @Override
    public List<DeviceResponse> getActiveDevicesWithSessions(String accountId, String currentSessionId) {
        log.info("Fetching active devices with sessions for accountId: {}", accountId);

        // Get all devices for the account from MongoDB
        List<Device> devices = deviceRepository.findByAccountId(accountId);
        log.info("Found {} devices for accountId: {}", devices.size(), accountId);

        List<DeviceResponse> activeDevices = new ArrayList<>();

        for (Device device : devices) {
            if (device.getSessionId() == null) {
                log.debug("Device {} has no sessionId, skipping", device.getId());
                continue;
            }

            // Check if there's an active session in Redis for this device
            Optional<RefreshTokenSession> sessionOpt = refreshTokenSessionRepository.findById(device.getSessionId());

            if (sessionOpt.isPresent()) {
                RefreshTokenSession session = sessionOpt.get();

                // Check if the session is still valid
                if (session.isValid()) {
                    boolean isCurrentDevice = device.getSessionId().equals(currentSessionId);

                    // Use existing mapper and add session fields
                    DeviceResponse response = new DeviceResponse(
                            device.getId(),
                            device.getDeviceId(),
                            device.getSessionId(),
                            device.getDeviceName(),
                            device.getBrowser(),
                            device.getOs(),
                            device.getDeviceType(),
                            device.getIpAddress(),
                            device.getLastActiveTime(),
                            device.getAccountId(),
                            device.getCreatedAt(),
                            device.getLastModifiedAt(),
                            device.getCreatedBy(),
                            device.getLastModifiedBy(),
                            session.getIssuedAt(),
                            session.getExpiresAt(),
                            isCurrentDevice,
                            true);

                    activeDevices.add(response);
                    log.debug("Device {} has active session, added to result", device.getId());
                } else {
                    log.debug("Device {} has expired or revoked session, skipping", device.getId());
                }
            } else {
                log.debug("No session found in Redis for device {} with sessionId: {}",
                        device.getId(), device.getSessionId());
            }
        }

        log.info("Found {} devices with active sessions for accountId: {}", activeDevices.size(), accountId);
        return activeDevices;
    }

    private boolean isSessionActive(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return refreshTokenSessionRepository.findById(sessionId)
                .map(RefreshTokenSession::isValid)
                .orElse(false);
    }
}
