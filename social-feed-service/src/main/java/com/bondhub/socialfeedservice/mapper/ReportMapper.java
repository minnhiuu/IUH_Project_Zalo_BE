package com.bondhub.socialfeedservice.mapper;

import com.bondhub.socialfeedservice.dto.response.report.ReportResponse;
import com.bondhub.socialfeedservice.dto.response.report.UserWarningResponse;
import com.bondhub.socialfeedservice.model.Report;
import com.bondhub.socialfeedservice.model.UserWarning;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReportMapper {

    @Mapping(source = "lastModifiedAt", target = "updatedAt")
    ReportResponse toReportResponse(Report report);

    @Mapping(source = "createdAt", target = "createdAt")
    UserWarningResponse toUserWarningResponse(UserWarning warning);
}
