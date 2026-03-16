package com.bondhub.authservice.dto.device.request;

import com.bondhub.authservice.enums.DeviceType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for updating an existing device.
 * <p>
 * This record is used to receive device update requests from clients.
 * All fields are optional - only provided fields will be updated.
 * </p>
 *
 * @param deviceId       the new device ID (optional)
 * @param sessionId      the new session ID (optional)
 * @param deviceName     the new device name (optional)
 * @param browser        the new browser information (optional)
 * @param os             the new OS information (optional)
 * @param deviceType     the new device type (optional)
 * @param ipAddress      the new IP address (optional)
 * @param lastActiveTime the new last active time (optional)
 * @param accountId      the new account ID (optional)
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceUpdateRequest(
        String deviceId,
        String sessionId,
        String deviceName,
        String browser,
        String os,
        DeviceType deviceType,
        String ipAddress,
        LocalDateTime lastActiveTime,
        String accountId) {
}
