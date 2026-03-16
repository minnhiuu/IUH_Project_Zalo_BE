package com.bondhub.searchservice.mapper;

import com.bondhub.searchservice.dto.response.FailedEventResponse;
import com.bondhub.searchservice.model.mongodb.FailedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface FailedEventMapper {
    FailedEventResponse toDto(FailedEvent entity);
    List<FailedEventResponse> toDtoList(List<FailedEvent> entities);
}
