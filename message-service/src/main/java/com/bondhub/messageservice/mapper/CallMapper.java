package com.bondhub.messageservice.mapper;

import com.bondhub.messageservice.dto.response.CallResponse;
import com.bondhub.messageservice.model.CallSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CallMapper {

    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "roomId", source = "session.roomId")
    @Mapping(target = "rtcToken", source = "rtcToken")
    @Mapping(target = "appId", source = "appId")
    CallResponse toCallResponse(CallSession session, String rtcToken, long appId);
}
