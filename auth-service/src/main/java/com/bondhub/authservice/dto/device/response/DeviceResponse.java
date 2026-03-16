package com.bondhub.authservice.dto.device.response;

import com.bondhub.authservice.enums.DeviceType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for device responses.
 * <p>
 * This record is used to send device information to clients.
 * </p>
 *
 * @param id              the unique identifier of the device
 * @param deviceId        the unique device ID from the client
 * @param sessionId       the session ID associated with the device
 * @param deviceName      the name of the device
 * @param browser         the browser information
 * @param os              the operating system information
 * @param deviceType      the type of device (WEB, MOBILE)
 * @param ipAddress       the IP address of the device
 * @param lastActiveTime  the last time the device was active (optional)
 * @param accountId       the account ID associated with the device
 * @param createdAt       the timestamp when the device was created
 * @param lastModifiedAt  the timestamp when the device was last modified
 * @param createdBy       the user who created the device
 * @param lastModifiedBy  the user who last modified the device
 * @param issuedAt        timestamp when the session was issued (optional, for
 *                        active sessions)
 * @param expiresAt       timestamp when the session expires (optional, for
 *                        active sessions)
 * @param isCurrentDevice indicates if this is the current device (optional, for
 *                        active sessions)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceResponse(
                String id,
                String deviceId,
                String sessionId,
                String deviceName,
                String browser,
                String os,
                DeviceType deviceType,
                String ipAddress,
                LocalDateTime lastActiveTime,
                String accountId,
                LocalDateTime createdAt,
                LocalDateTime lastModifiedAt,
                String createdBy,
                String lastModifiedBy,
                Long issuedAt,
                Long expiresAt,
                Boolean isCurrentDevice,
                Boolean isActive) {
}
