package com.bondhub.authservice.mapper;

import com.bondhub.authservice.dto.device.request.DeviceCreateRequest;
import com.bondhub.authservice.dto.device.request.DeviceUpdateRequest;
import com.bondhub.authservice.dto.device.response.DeviceResponse;
import com.bondhub.authservice.model.Device;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper interface for converting between Device entities and DTOs.
 * <p>
 * This interface uses MapStruct to map Device entities to response DTOs
 * and request DTOs to Device entities.
 * </p>
 *
 * @author BondHub Development Team
 * @version 1.0
 * @since 2026-02-04
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DeviceMapper {

    DeviceResponse toResponse(Device device);

    DeviceResponse toResponse(Device device, Boolean isActive);

    Device toEntity(DeviceCreateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(@MappingTarget Device device, DeviceUpdateRequest request);
}
