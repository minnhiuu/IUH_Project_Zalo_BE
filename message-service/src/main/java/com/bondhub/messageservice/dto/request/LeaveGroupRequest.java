package com.bondhub.messageservice.dto.request;

public record LeaveGroupRequest(
        boolean silent,
        String transferTo,
        boolean blockReJoin
) {
}
