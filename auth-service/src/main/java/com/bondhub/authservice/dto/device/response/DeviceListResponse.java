package com.bondhub.authservice.dto.device.response;

import java.util.List;

/**
 * Data Transfer Object for grouped device responses.
 *
 * <p>
 * Field names are preserved for API compatibility:
 * <ul>
 * <li>{@code activeDevices}: active devices</li>
 * <li>{@code otherDevices}: inactive devices</li>
 * </ul>
 * </p>
 *
 * @param activeDevices the active devices
 * @param otherDevices  the inactive devices
 */
public record DeviceListResponse(
        List<DeviceResponse> activeDevices,
        List<DeviceResponse> otherDevices) {
}
