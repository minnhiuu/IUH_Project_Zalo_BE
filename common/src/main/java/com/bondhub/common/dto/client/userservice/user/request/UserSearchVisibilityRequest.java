package com.bondhub.common.dto.client.userservice.user.request;

import lombok.Builder;

import java.util.List;

@Builder
public record UserSearchVisibilityRequest(
        List<String> targetUserIds
) {
}
