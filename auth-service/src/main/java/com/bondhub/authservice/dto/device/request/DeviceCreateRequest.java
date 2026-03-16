package com.bondhub.authservice.dto.device.request;

import com.bondhub.authservice.enums.DeviceType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for creating a new device.
 * <p>
 * This record is used to receive device creation requests from clients.
 * All required fields are validated to ensure data integrity.
 * </p>
 *
 * @param deviceId      the unique device ID from the client
 * @param sessionId     the session ID associated with the device, must not be blank
 * @param deviceName    the name of the device, must not be blank
 * @param browser       the browser information
 * @param os            the operating system information
 * @param deviceType    the type of device (WEB, MOBILE), must not be null
 * @param ipAddress     the IP address of the device
 * @param lastActiveTime the last time the device was active (optional)
 * @param accountId     the account ID associated with the device, must not be blank
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceCreateRequest(
        String deviceId,

        @NotBlank(message = "{validation.sessionId.required}") String sessionId,

        @NotBlank(message = "{validation.deviceName.required}") String deviceName,

        String browser,

        String os,

        @NotNull(message = "{validation.deviceType.required}") DeviceType deviceType,

        String ipAddress,

        LocalDateTime lastActiveTime,

        @NotBlank(message = "{validation.accountId.required}") String accountId) {
}
