package com.bondhub.authservice.service.device;

import com.bondhub.authservice.dto.device.request.DeviceCreateRequest;
import com.bondhub.authservice.dto.device.request.DeviceUpdateRequest;
import com.bondhub.authservice.dto.device.response.DeviceListResponse;
import com.bondhub.authservice.dto.device.response.DeviceResponse;
import com.bondhub.common.exception.AppException;

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
     * Deletes a device by id for a specific account.
     *
     * @param id        the device id to delete
     * @param accountId the authenticated account id
     * @throws AppException if device is not found, does not belong to account, or
     *                      is currently active
     */
    void deleteDeviceById(String id, String accountId);

    /**
     * Retrieves all devices for an account and groups them by session state.
     *
     * <p>
     * For backward compatibility, the response fields are kept as:
     * <ul>
     * <li>{@code activeDevices}: active devices</li>
     * <li>{@code otherDevices}: inactive devices</li>
     * </ul>
     * </p>
     *
     * @param accountId        the account ID to search for, must not be null
     * @param currentSessionId the current session ID to mark the current device
     * @return a grouped list of active and inactive devices
     */
    DeviceListResponse getGroupedActiveDevicesWithSessions(String accountId, String currentSessionId);
}
