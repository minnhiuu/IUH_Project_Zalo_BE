package com.bondhub.common.dto.client.friendservice;

import lombok.Builder;

import java.util.List;

@Builder
public record UserSearchContextRequest(
        List<String> targetUserIds
) {
}
