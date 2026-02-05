package com.bondhub.authservice.service.device;

import com.bondhub.authservice.dto.device.request.DeviceCreateRequest;
import com.bondhub.authservice.dto.device.request.DeviceUpdateRequest;
import com.bondhub.authservice.dto.device.response.DeviceResponse;
import com.bondhub.authservice.mapper.DeviceMapper;
import com.bondhub.authservice.model.Device;
import com.bondhub.authservice.repository.DeviceRepository;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DeviceServiceImpl implements DeviceService {

    DeviceRepository deviceRepository;
    DeviceMapper deviceMapper;

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
        return deviceMapper.toResponse(savedDevice);
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
        return deviceMapper.toResponse(device);
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
        return deviceMapper.toResponse(device);
    }

    @Override
    public List<DeviceResponse> getDevicesByAccountId(String accountId) {
        log.info("Fetching devices for accountId: {}", accountId);
        List<Device> devices = deviceRepository.findByAccountId(accountId);
        log.info("Found {} devices for accountId: {}", devices.size(), accountId);
        return devices.stream()
                .map(deviceMapper::toResponse)
                .toList();
    }

    @Override
    public List<DeviceResponse> getAllDevices() {
        log.info("Fetching all devices");
        List<Device> devices = deviceRepository.findAll();
        log.info("Found {} devices", devices.size());
        return devices.stream()
                .map(deviceMapper::toResponse)
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
        return deviceMapper.toResponse(updatedDevice);
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
        return deviceMapper.toResponse(updatedDevice);
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
}
