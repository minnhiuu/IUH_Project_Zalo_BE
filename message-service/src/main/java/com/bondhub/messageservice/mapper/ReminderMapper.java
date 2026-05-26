package com.bondhub.messageservice.mapper;

import com.bondhub.messageservice.dto.request.ReminderRequest;
import com.bondhub.messageservice.dto.response.ReminderResponse;
import com.bondhub.messageservice.model.Reminder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReminderMapper {

    default Instant map(LocalDateTime value) {
        if (value == null) return null;
        return value.toInstant(ZoneOffset.UTC);
    }

    Reminder toEntity(ReminderRequest request);
    @Mapping(source = "lastModifiedAt", target = "updatedAt")
    ReminderResponse toResponse(Reminder entity);
}