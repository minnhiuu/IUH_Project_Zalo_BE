package com.bondhub.authservice.service.device;

import com.bondhub.authservice.dto.device.request.DeviceCreateRequest;
import com.bondhub.authservice.dto.device.request.DeviceUpdateRequest;
import com.bondhub.authservice.dto.device.response.DeviceResponse;

import java.util.List;

/**
 * Service interface for managing Device operations.
 * <p>
 * This service provides CRUD operations for Device entities using DTOs
 * and additional utility methods for checking device existence and retrieving
 * devices by account.
 * All methods return appropriate response objects for consistent response
 * handling.
 * </p>
 *
 * @author BondHub Development Team
 * @version 1.0
 * @since 2026-02-04
 */
public interface DeviceService {

    /**
     * Creates a new device in the system.
     * <p>
     * Validates that the session ID is unique before creating the device.
     * </p>
     *
     * @param request the device creation request DTO, must not be null
     * @return the created device response DTO with generated ID
     * @throws AppException if session ID already exists
     *                      (DEV_SESSION_ID_ALREADY_USED)
     */
    DeviceResponse createDevice(DeviceCreateRequest request);

    /**
     * Retrieves a device by its unique identifier.
     *
     * @param id the unique identifier of the device, must not be null
     * @return the device response DTO if found
     * @throws AppException if device not found (DEV_DEVICE_NOT_FOUND)
     */
    DeviceResponse getDeviceById(String id);

    /**
     * Retrieves a device by session ID.
     *
     * @param sessionId the session ID to search for, must not be null
     * @return the device response DTO if found
     * @throws AppException if device not found (DEV_DEVICE_NOT_FOUND)
     */
    DeviceResponse getDeviceBySessionId(String sessionId);

    /**
     * Retrieves all devices associated with an account.
     *
     * @param accountId the account ID to search for, must not be null
     * @return a list of device response DTOs (may be empty)
     */
    List<DeviceResponse> getDevicesByAccountId(String accountId);

    /**
     * Retrieves all devices in the system.
     *
     * @return a list of all device response DTOs (may be empty)
     */
    List<DeviceResponse> getAllDevices();

    /**
     * Updates an existing device with new information.
     * <p>
     * Only the provided fields will be updated. If session ID is changed,
     * validates that the new value is unique before updating.
     * </p>
     *
     * @param id      the unique identifier of the device to update, must not be
     *                null
     * @param request the device update request DTO containing updated information
     * @return the updated device response DTO
     * @throws AppException if device not found (DEV_DEVICE_NOT_FOUND)
     *                      or session ID already exists
     *                      (DEV_SESSION_ID_ALREADY_USED)
     */
    DeviceResponse updateDevice(String id, DeviceUpdateRequest request);

    /**
     * Updates an existing device by session ID.
     * <p>
     * Only the provided fields will be updated.
     * </p>
     *
     * @param sessionId the session ID of the device to update, must not be null
     * @param request   the device update request DTO containing updated information
     * @return the updated device response DTO
     * @throws AppException if device not found (DEV_DEVICE_NOT_FOUND)
     */
    DeviceResponse updateDeviceBySessionId(String sessionId, DeviceUpdateRequest request);

    /**
     * Deletes a device from the system.
     *
     * @param id the unique identifier of the device to delete, must not be null
     * @throws AppException if device not found (DEV_DEVICE_NOT_FOUND)
     */
    void deleteDevice(String id);

    /**
     * Deletes all devices associated with an account.
     *
     * @param accountId the account ID, must not be null
     */
    void deleteDevicesByAccountId(String accountId);

    /**
     * Checks if a device with the specified session ID exists.
     *
     * @param sessionId the session ID to check, must not be null
     * @return true if device exists, false otherwise
     */
    boolean existsBySessionId(String sessionId);

    /**
     * Saves or updates a device in MongoDB during login/register.
     * <p>
     * If a device with the same {@code deviceId} and {@code accountId} already
     * exists
     * it will be updated (new sessionId, ipAddress, lastActiveTime). Otherwise a
     * new
     * device document is created.
     * </p>
     *
     * @param request device creation payload (deviceId, sessionId, accountId, etc.)
     * @return the saved/updated device response DTO
     */
    DeviceResponse saveOrUpdateDevice(DeviceCreateRequest request);

    /**
     * Retrieves all active devices with active sessions for a given account.
     * <p>
     * This method returns devices that have valid (non-expired and non-revoked)
     * refresh token sessions in Redis. The response includes session details
     * (issuedAt, expiresAt, isCurrentDevice).
     * </p>
     *
     * @param accountId        the account ID to search for, must not be null
     * @param currentSessionId the current session ID to mark the current device
     *                         (optional)
     * @return a list of device response DTOs with session information (may be
     *         empty)
     */
    List<DeviceResponse> getActiveDevicesWithSessions(String accountId, String currentSessionId);
}
